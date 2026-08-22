package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.identity.config.IdentityProperties;
import com.luislipinski.trucklife.identity.domain.UserActionTokenPurpose;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenEntity;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentityAccountWriter {

    private final UserRepository userRepository;
    private final UserActionTokenRepository actionTokenRepository;
    private final ActionTokenGenerator tokenGenerator;
    private final IdentityProperties properties;
    private final Clock clock;

    public IdentityAccountWriter(
            UserRepository userRepository,
            UserActionTokenRepository actionTokenRepository,
            ActionTokenGenerator tokenGenerator,
            IdentityProperties properties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.actionTokenRepository = actionTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<PendingVerificationDelivery> createPendingAccount(
            String email,
            String normalizedEmail,
            String displayName,
            String passwordHash
    ) {
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                normalizedEmail,
                passwordHash,
                displayName,
                UserStatus.PENDING_VERIFICATION,
                UserRole.USER,
                false,
                null,
                now,
                now,
                null
        ));
        return Optional.of(createVerificationToken(user, now));
    }

    @Transactional
    public Optional<PendingVerificationDelivery> resendVerification(String normalizedEmail) {
        Instant now = clock.instant();
        Optional<UserEntity> candidate = userRepository
                .findByNormalizedEmailForUpdate(normalizedEmail);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        UserEntity user = candidate.orElseThrow();
        if (user.isEmailVerified() || user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            return Optional.empty();
        }

        actionTokenRepository.markActiveTokensAsUsed(
                user.getId(),
                UserActionTokenPurpose.EMAIL_VERIFICATION,
                now
        );
        return Optional.of(createVerificationToken(user, now));
    }

    @Transactional
    public void verifyEmail(String tokenHash) {
        Instant now = clock.instant();
        UserActionTokenEntity token = actionTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidVerificationToken);

        if (token.getPurpose() != UserActionTokenPurpose.EMAIL_VERIFICATION) {
            throw invalidVerificationToken();
        }
        if (token.getUsedAt() != null) {
            if (token.getUser().isEmailVerified()) {
                return;
            }
            throw invalidVerificationToken();
        }
        if (!now.isBefore(token.getExpiresAt())) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "EMAIL_VERIFICATION_TOKEN_EXPIRED",
                    "Verification token expired",
                    "The email verification token has expired"
            );
        }

        token.markUsed(now);
        token.getUser().verifyEmail(now);
    }

    private PendingVerificationDelivery createVerificationToken(UserEntity user, Instant now) {
        ActionTokenGenerator.GeneratedActionToken generated = tokenGenerator.generate();
        Instant expiresAt = now.plus(properties.emailVerificationTtl());
        actionTokenRepository.saveAndFlush(new UserActionTokenEntity(
                UUID.randomUUID(),
                user,
                UserActionTokenPurpose.EMAIL_VERIFICATION,
                generated.tokenHash(),
                expiresAt,
                null,
                now
        ));
        return new PendingVerificationDelivery(
                user.getEmail(),
                user.getDisplayName(),
                generated.rawToken(),
                expiresAt
        );
    }

    private ApiProblemException invalidVerificationToken() {
        return new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "EMAIL_VERIFICATION_TOKEN_INVALID",
                "Invalid verification token",
                "The email verification token is invalid"
        );
    }
}
