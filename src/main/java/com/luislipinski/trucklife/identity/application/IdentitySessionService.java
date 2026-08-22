package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.identity.config.IdentitySessionProperties;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenEntity;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentitySessionService implements IdentitySessionOperations {

    private static final int MAXIMUM_EMAIL_LENGTH = 320;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private static final int MAXIMUM_IP_ADDRESS_LENGTH = 64;
    private static final int MAXIMUM_USER_AGENT_LENGTH = 500;
    private static final String DUMMY_PASSWORD = "timing-only-password-value";
    private static final Pattern REFRESH_TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtAccessTokenIssuer accessTokenIssuer;
    private final IdentitySessionProperties properties;
    private final IdentityRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyPasswordHash;

    public IdentitySessionService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            JwtAccessTokenIssuer accessTokenIssuer,
            IdentitySessionProperties properties,
            IdentityRateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.accessTokenIssuer = accessTokenIssuer;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Override
    @Transactional
    public IssuedSession login(
            String email,
            String rawPassword,
            String clientAddress,
            String userAgent
    ) {
        String normalizedEmail = normalizeEmail(email);
        String safeAddress = safeClientAddress(clientAddress);
        rateLimiter.checkLogin(TokenDigests.sha256(normalizedEmail), safeAddress);

        Optional<UserEntity> candidate = normalizedEmail.isBlank()
                || normalizedEmail.length() > MAXIMUM_EMAIL_LENGTH
                ? Optional.empty()
                : userRepository.findByNormalizedEmail(normalizedEmail);
        boolean passwordWithinLimit = rawPassword != null
                && rawPassword.codePointCount(0, rawPassword.length()) <= MAXIMUM_PASSWORD_LENGTH;
        String passwordForComparison = passwordWithinLimit ? rawPassword : DUMMY_PASSWORD;
        String hashForComparison = candidate.isPresent() && passwordWithinLimit
                ? candidate.orElseThrow().getPasswordHash()
                : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(
                passwordForComparison,
                hashForComparison
        );

        if (candidate.isEmpty() || !passwordWithinLimit || !passwordMatches) {
            throw invalidCredentials();
        }

        UserEntity user = candidate.orElseThrow();
        requireAccountEligibleForSession(user);
        String upgradedPasswordHash = passwordEncoder.upgradeEncoding(user.getPasswordHash())
                ? passwordEncoder.encode(rawPassword)
                : null;
        Instant now = clock.instant();
        UUID familyId = UUID.randomUUID();
        RefreshTokenGenerator.GeneratedRefreshToken refresh = refreshTokenGenerator.generate();
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        refreshTokenRepository.saveAndFlush(new RefreshTokenEntity(
                UUID.randomUUID(),
                user,
                familyId,
                null,
                refresh.tokenHash(),
                now,
                refreshExpiresAt,
                null,
                null,
                safeAddress,
                sanitize(userAgent, MAXIMUM_USER_AGENT_LENGTH)
        ));
        user.recordSuccessfulLogin(now, upgradedPasswordHash);
        JwtAccessTokenIssuer.IssuedAccessToken access = accessTokenIssuer.issue(user, familyId);
        return new IssuedSession(
                access.token(),
                access.expiresAt(),
                refresh.rawToken(),
                refreshExpiresAt
        );
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public IssuedSession refresh(
            String rawRefreshToken,
            String clientAddress,
            String userAgent
    ) {
        String safeAddress = safeClientAddress(clientAddress);
        if (rawRefreshToken == null || !REFRESH_TOKEN_FORMAT.matcher(rawRefreshToken).matches()) {
            rateLimiter.checkUnknownRefresh(safeAddress);
            throw rawRefreshToken == null
                    ? RefreshTokenException.required()
                    : RefreshTokenException.invalid();
        }

        String tokenHash = TokenDigests.sha256(rawRefreshToken);
        Optional<RefreshTokenEntity> candidate = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash);
        if (candidate.isEmpty()) {
            rateLimiter.checkUnknownRefresh(safeAddress);
            throw RefreshTokenException.invalid();
        }

        RefreshTokenEntity current = candidate.orElseThrow();
        rateLimiter.checkRefresh(current.getFamilyId().toString(), safeAddress);
        Instant now = clock.instant();
        if (current.getReplacedBy() != null) {
            current.markReuseDetected(now);
            refreshTokenRepository.saveAndFlush(current);
            refreshTokenRepository.revokeActiveFamily(current.getFamilyId(), now);
            throw RefreshTokenException.reused();
        }
        if (current.getRevokedAt() != null) {
            throw RefreshTokenException.invalid();
        }
        if (!now.isBefore(current.getExpiresAt())) {
            current.markRevoked(now);
            refreshTokenRepository.saveAndFlush(current);
            throw RefreshTokenException.expired();
        }

        UserEntity user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            refreshTokenRepository.revokeActiveFamily(current.getFamilyId(), now);
            throw RefreshTokenException.invalid();
        }

        RefreshTokenGenerator.GeneratedRefreshToken refresh = refreshTokenGenerator.generate();
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        RefreshTokenEntity replacement = refreshTokenRepository.saveAndFlush(
                new RefreshTokenEntity(
                        UUID.randomUUID(),
                        user,
                        current.getFamilyId(),
                        current,
                        refresh.tokenHash(),
                        now,
                        refreshExpiresAt,
                        null,
                        null,
                        safeAddress,
                        sanitize(userAgent, MAXIMUM_USER_AGENT_LENGTH)
                )
        );
        current.markReplacedBy(replacement, now);
        refreshTokenRepository.saveAndFlush(current);

        JwtAccessTokenIssuer.IssuedAccessToken access = accessTokenIssuer.issue(
                user,
                current.getFamilyId()
        );
        return new IssuedSession(
                access.token(),
                access.expiresAt(),
                refresh.rawToken(),
                refreshExpiresAt
        );
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || !REFRESH_TOKEN_FORMAT.matcher(rawRefreshToken).matches()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(TokenDigests.sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeActiveFamily(
                        token.getFamilyId(),
                        clock.instant()
                ));
    }

    private void requireAccountEligibleForSession(UserEntity user) {
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION || !user.isEmailVerified()) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED",
                    "E-mail not verified",
                    "The account e-mail must be verified before login"
            );
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_LOCKED",
                    "Account locked",
                    "The account is locked"
            );
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "Account disabled",
                    "The account is disabled"
            );
        }
    }

    private ApiProblemException invalidCredentials() {
        return new ApiProblemException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Authentication failed",
                "Invalid e-mail or password"
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private String safeClientAddress(String clientAddress) {
        String value = clientAddress == null || clientAddress.isBlank()
                ? "unknown"
                : clientAddress.strip();
        return sanitize(value, MAXIMUM_IP_ADDRESS_LENGTH);
    }

    private String sanitize(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= maximumLength
                ? stripped
                : stripped.substring(0, maximumLength);
    }
}
