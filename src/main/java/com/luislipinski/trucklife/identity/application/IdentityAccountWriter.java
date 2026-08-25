package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.identity.config.IdentityProperties;
import com.luislipinski.trucklife.identity.domain.UserActionTokenPurpose;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenEntity;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentityAccountWriter {

    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final UserActionTokenRepository actionTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ActionTokenGenerator tokenGenerator;
    private final IdentityProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public IdentityAccountWriter(UserRepository userRepository, UserActionTokenRepository actionTokenRepository, RefreshTokenRepository refreshTokenRepository, ActionTokenGenerator tokenGenerator, IdentityProperties properties, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.actionTokenRepository = actionTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Optional<PendingVerificationDelivery> createPendingAccount(String email, String normalizedEmail, String displayName, String passwordHash) {
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        UserEntity user = userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(), email, normalizedEmail, passwordHash, displayName, UserStatus.PENDING_VERIFICATION, UserRole.USER, false, null, now, now, null));
        return Optional.of(createVerificationToken(user, now));
    }

    @Transactional
    public Optional<PendingVerificationDelivery> resendVerification(String normalizedEmail) {
        Instant now = clock.instant();
        Optional<UserEntity> candidate = userRepository.findByNormalizedEmailForUpdate(normalizedEmail);
        if (candidate.isEmpty()) return Optional.empty();
        UserEntity user = candidate.orElseThrow();
        if (user.isEmailVerified() || user.getStatus() != UserStatus.PENDING_VERIFICATION) return Optional.empty();
        actionTokenRepository.markActiveTokensAsUsed(user.getId(), UserActionTokenPurpose.EMAIL_VERIFICATION, now);
        return Optional.of(createVerificationToken(user, now));
    }

    @Transactional
    public void verifyEmail(String tokenHash) {
        Instant now = clock.instant();
        UserActionTokenEntity token = actionTokenRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(this::invalidVerificationToken);
        if (token.getPurpose() != UserActionTokenPurpose.EMAIL_VERIFICATION) throw invalidVerificationToken();
        if (token.getUsedAt() != null) {
            if (token.getUser().isEmailVerified()) return;
            throw invalidVerificationToken();
        }
        if (!now.isBefore(token.getExpiresAt())) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_TOKEN_EXPIRED", "Verification token expired", "The email verification token has expired");
        }
        token.markUsed(now);
        token.getUser().verifyEmail(now);
    }

    @Transactional
    public Optional<PendingPasswordResetDelivery> requestPasswordReset(String normalizedEmail) {
        Instant now = clock.instant();
        Optional<UserEntity> candidate = userRepository.findByNormalizedEmailForUpdate(normalizedEmail);
        if (candidate.isEmpty()) return Optional.empty();
        UserEntity user = candidate.orElseThrow();
        if (!user.isEmailVerified() || user.getStatus() != UserStatus.ACTIVE) return Optional.empty();
        actionTokenRepository.markActiveTokensAsUsed(user.getId(), UserActionTokenPurpose.PASSWORD_RESET, now);
        ActionTokenGenerator.GeneratedActionToken generated = tokenGenerator.generate();
        Instant expiresAt = now.plus(PASSWORD_RESET_TTL);
        actionTokenRepository.saveAndFlush(new UserActionTokenEntity(UUID.randomUUID(), user, UserActionTokenPurpose.PASSWORD_RESET, generated.tokenHash(), expiresAt, null, now));
        return Optional.of(new PendingPasswordResetDelivery(user.getEmail(), user.getDisplayName(), generated.rawToken(), expiresAt));
    }

    @Transactional
    public void resetPassword(String tokenHash, String newPasswordHash) {
        Instant now = clock.instant();
        UserActionTokenEntity token = actionTokenRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(this::invalidPasswordResetToken);
        if (token.getPurpose() != UserActionTokenPurpose.PASSWORD_RESET || token.getUsedAt() != null) throw invalidPasswordResetToken();
        if (!now.isBefore(token.getExpiresAt())) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_EXPIRED", "Password reset token expired", "The password reset token has expired");
        }
        UserEntity user = token.getUser();
        token.markUsed(now);
        actionTokenRepository.markActiveTokensAsUsed(user.getId(), UserActionTokenPurpose.PASSWORD_RESET, now);
        user.changePassword(newPasswordHash, now);
        refreshTokenRepository.revokeActiveForUser(user.getId(), now);
    }

    @Transactional
    public void changePassword(UUID userId, String currentRawPassword, String newPasswordHash) {
        Instant now = clock.instant();
        UserEntity user = userRepository.findByIdForUpdate(userId).orElseThrow(this::authenticatedAccountUnavailable);
        if (!passwordEncoder.matches(currentRawPassword, user.getPasswordHash())) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "CURRENT_PASSWORD_INVALID",
                    "Current password invalid",
                    "The current password is invalid"
            );
        }

        actionTokenRepository.markActiveTokensAsUsed(user.getId(), UserActionTokenPurpose.PASSWORD_RESET, now);
        user.changePassword(newPasswordHash, now);
        refreshTokenRepository.revokeActiveForUser(user.getId(), now);
    }

    private PendingVerificationDelivery createVerificationToken(UserEntity user, Instant now) {
        ActionTokenGenerator.GeneratedActionToken generated = tokenGenerator.generate();
        Instant expiresAt = now.plus(properties.emailVerificationTtl());
        actionTokenRepository.saveAndFlush(new UserActionTokenEntity(UUID.randomUUID(), user, UserActionTokenPurpose.EMAIL_VERIFICATION, generated.tokenHash(), expiresAt, null, now));
        return new PendingVerificationDelivery(user.getEmail(), user.getDisplayName(), generated.rawToken(), expiresAt);
    }

    private ApiProblemException invalidVerificationToken() {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_TOKEN_INVALID", "Invalid verification token", "The email verification token is invalid");
    }

    private ApiProblemException invalidPasswordResetToken() {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_INVALID", "Invalid password reset token", "The password reset token is invalid");
    }

    private ApiProblemException authenticatedAccountUnavailable() {
        return new ApiProblemException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication required", "The authenticated account is no longer available");
    }
}
