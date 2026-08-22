package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.identity.email.VerificationEmailDeliveryPort;
import java.sql.SQLException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class IdentityAccountService implements IdentityAccountOperations {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final IdentityAccountWriter accountWriter;
    private final IdentityRateLimiter rateLimiter;
    private final VerificationEmailDeliveryPort emailDelivery;
    private final PasswordEncoder passwordEncoder;

    public IdentityAccountService(
            IdentityAccountWriter accountWriter,
            IdentityRateLimiter rateLimiter,
            VerificationEmailDeliveryPort emailDelivery,
            PasswordEncoder passwordEncoder
    ) {
        this.accountWriter = accountWriter;
        this.rateLimiter = rateLimiter;
        this.emailDelivery = emailDelivery;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void register(
            String email,
            String displayName,
            String rawPassword,
            String clientAddress
    ) {
        rateLimiter.checkRegistration(safeClientAddress(clientAddress));
        String trimmedEmail = email.strip();
        String normalizedEmail = normalizeEmail(trimmedEmail);
        String passwordHash = passwordEncoder.encode(rawPassword);

        try {
            accountWriter.createPendingAccount(
                    trimmedEmail,
                    normalizedEmail,
                    displayName.strip(),
                    passwordHash
            ).ifPresent(this::deliver);
        } catch (DataIntegrityViolationException exception) {
            if (!isUniqueViolation(exception)) {
                throw exception;
            }
            // A concurrent request created the same normalized e-mail first.
            // Preserve the neutral registration response.
        }
    }

    @Override
    public void verifyEmail(String rawToken, String clientAddress) {
        String tokenHash = TokenDigests.sha256(rawToken);
        rateLimiter.checkEmailVerification(tokenHash, safeClientAddress(clientAddress));
        accountWriter.verifyEmail(tokenHash);
    }

    @Override
    public void resendVerification(String email, String clientAddress) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedEmailHash = TokenDigests.sha256(normalizedEmail);
        rateLimiter.checkResendVerification(
                normalizedEmailHash,
                safeClientAddress(clientAddress)
        );
        accountWriter.resendVerification(normalizedEmail).ifPresent(this::deliver);
    }

    private void deliver(PendingVerificationDelivery delivery) {
        emailDelivery.sendVerificationEmail(
                delivery.recipient(),
                delivery.displayName(),
                delivery.rawToken(),
                delivery.expiresAt()
        );
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String safeClientAddress(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank()
                ? "unknown"
                : clientAddress;
    }

    private boolean isUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
