package com.luislipinski.trucklife.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.luislipinski.trucklife.identity.config.IdentitySessionProperties;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenEntity;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentitySessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final String VALID_REFRESH = "A".repeat(43);

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenGenerator refreshTokenGenerator;
    private JwtAccessTokenIssuer accessTokenIssuer;
    private IdentityRateLimiter rateLimiter;
    private PasswordEncoder passwordEncoder;
    private IdentitySessionService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        refreshTokenGenerator = mock(RefreshTokenGenerator.class);
        accessTokenIssuer = mock(JwtAccessTokenIssuer.class);
        rateLimiter = mock(IdentityRateLimiter.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("dummy-password-hash");
        service = new IdentitySessionService(
                userRepository,
                refreshTokenRepository,
                refreshTokenGenerator,
                accessTokenIssuer,
                properties(),
                rateLimiter,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void requiresARefreshTokenAndRateLimitsTheUnknownAddress() {
        RefreshTokenException exception = assertThrows(
                RefreshTokenException.class,
                () -> service.refresh(null, null, "browser")
        );

        assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_REQUIRED");
        verify(rateLimiter).checkUnknownRefresh("unknown");
        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(any());
    }

    @Test
    void detectsReusedRefreshTokenAndRevokesItsWholeFamily() {
        UserEntity user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity current = refreshToken(
                user,
                familyId,
                VALID_REFRESH,
                NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(20))
        );
        RefreshTokenEntity replacement = refreshToken(
                user,
                familyId,
                "B".repeat(43),
                NOW.minus(Duration.ofHours(1)),
                NOW.plus(Duration.ofDays(29))
        );
        current.markReplacedBy(replacement, NOW.minus(Duration.ofMinutes(30)));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenDigests.sha256(VALID_REFRESH)))
                .thenReturn(Optional.of(current));

        RefreshTokenException exception = assertThrows(
                RefreshTokenException.class,
                () -> service.refresh(VALID_REFRESH, "203.0.113.10", "browser")
        );

        assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_REUSED");
        assertThat(current.getReuseDetectedAt()).isEqualTo(NOW);
        verify(rateLimiter).checkRefresh(familyId.toString(), "203.0.113.10");
        verify(refreshTokenRepository).saveAndFlush(current);
        verify(refreshTokenRepository).revokeActiveFamily(familyId, NOW);
    }

    @Test
    void revokesAnExpiredRefreshTokenBeforeRejectingIt() {
        UserEntity user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity expired = refreshToken(
                user,
                familyId,
                VALID_REFRESH,
                NOW.minus(Duration.ofDays(31)),
                NOW
        );
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenDigests.sha256(VALID_REFRESH)))
                .thenReturn(Optional.of(expired));

        RefreshTokenException exception = assertThrows(
                RefreshTokenException.class,
                () -> service.refresh(VALID_REFRESH, "203.0.113.11", "browser")
        );

        assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_EXPIRED");
        assertThat(expired.getRevokedAt()).isEqualTo(NOW);
        verify(refreshTokenRepository).saveAndFlush(expired);
        verify(refreshTokenRepository, never()).revokeActiveFamily(familyId, NOW);
    }

    @Test
    void revokesTheFamilyWhenTheAccountBecomesIneligible() {
        UserEntity disabled = user(UserStatus.DISABLED, true);
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity current = refreshToken(
                disabled,
                familyId,
                VALID_REFRESH,
                NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(20))
        );
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenDigests.sha256(VALID_REFRESH)))
                .thenReturn(Optional.of(current));

        RefreshTokenException exception = assertThrows(
                RefreshTokenException.class,
                () -> service.refresh(VALID_REFRESH, "203.0.113.12", "browser")
        );

        assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_INVALID");
        verify(refreshTokenRepository).revokeActiveFamily(familyId, NOW);
        verify(refreshTokenGenerator, never()).generate();
        verify(accessTokenIssuer, never()).issue(any(), any());
    }

    @Test
    void rotatesAValidRefreshTokenAndIssuesANewAccessToken() {
        UserEntity user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity current = refreshToken(
                user,
                familyId,
                VALID_REFRESH,
                NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(20))
        );
        String replacementRaw = "B".repeat(43);
        RefreshTokenGenerator.GeneratedRefreshToken generated =
                new RefreshTokenGenerator.GeneratedRefreshToken(
                        replacementRaw,
                        TokenDigests.sha256(replacementRaw)
                );
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenDigests.sha256(VALID_REFRESH)))
                .thenReturn(Optional.of(current));
        when(refreshTokenGenerator.generate()).thenReturn(generated);
        when(refreshTokenRepository.saveAndFlush(any(RefreshTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accessTokenIssuer.issue(user, familyId)).thenReturn(
                new JwtAccessTokenIssuer.IssuedAccessToken(
                        "new-access-token",
                        NOW.plus(Duration.ofMinutes(10))
                )
        );

        IssuedSession session = service.refresh(
                VALID_REFRESH,
                "203.0.113.13",
                "test-agent"
        );

        assertThat(session.accessToken()).isEqualTo("new-access-token");
        assertThat(session.rawRefreshToken()).isEqualTo(replacementRaw);
        assertThat(session.accessTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(session.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(current.getReplacedBy()).isNotNull();
        assertThat(current.getReplacedBy().getFamilyId()).isEqualTo(familyId);
        assertThat(current.getReplacedBy().getParent()).isSameAs(current);
        assertThat(current.getReplacedBy().getCreatedIp()).isEqualTo("203.0.113.13");
        assertThat(current.getReplacedBy().getUserAgent()).isEqualTo("test-agent");
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        verify(accessTokenIssuer).issue(user, familyId);
    }

    @Test
    void logoutIsIdempotentAndRevokesOnlyKnownValidFamilies() {
        UserEntity user = activeUser();
        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity token = refreshToken(
                user,
                familyId,
                VALID_REFRESH,
                NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(20))
        );
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenDigests.sha256(VALID_REFRESH)))
                .thenReturn(Optional.of(token));

        service.logout(null);
        service.logout("malformed");
        service.logout(VALID_REFRESH);

        verify(refreshTokenRepository).findByTokenHashForUpdate(TokenDigests.sha256(VALID_REFRESH));
        verify(refreshTokenRepository).revokeActiveFamily(familyId, NOW);
    }

    private IdentitySessionProperties properties() {
        String secret = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        );
        return new IdentitySessionProperties(
                Duration.ofMinutes(10),
                Duration.ofDays(30),
                secret,
                "truck-life-simulator-api",
                "truck-life-simulator-web",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        );
    }

    private UserEntity activeUser() {
        return user(UserStatus.ACTIVE, true);
    }

    private UserEntity user(UserStatus status, boolean emailVerified) {
        return new UserEntity(
                UUID.randomUUID(),
                "driver@example.com",
                "driver@example.com",
                "encoded-password",
                "Road Driver",
                status,
                UserRole.USER,
                emailVerified,
                emailVerified ? NOW.minus(Duration.ofDays(2)) : null,
                NOW.minus(Duration.ofDays(10)),
                NOW.minus(Duration.ofDays(1)),
                NOW.minus(Duration.ofHours(2))
        );
    }

    private RefreshTokenEntity refreshToken(
            UserEntity user,
            UUID familyId,
            String rawToken,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new RefreshTokenEntity(
                UUID.randomUUID(),
                user,
                familyId,
                null,
                TokenDigests.sha256(rawToken),
                createdAt,
                expiresAt,
                null,
                null,
                "127.0.0.1",
                "test"
        );
    }
}
