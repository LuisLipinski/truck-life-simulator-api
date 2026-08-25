package com.luislipinski.trucklife.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenEntity;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
class ChangePasswordIntegrationTest {

    private static final String CHANGE_PASSWORD_PATH = "/api/v1/me/change-password";
    private static final String CURRENT_PASSWORD = "current password valid";
    private static final String NEW_PASSWORD = "new password valid 2026";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void requiresBearerAuthenticationForTheChangePasswordRoute() {
        restTestClient.post()
                .uri(CHANGE_PASSWORD_PATH)
                .body(new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void rejectsANewPasswordOutsideTheExistingPasswordPolicy() {
        UserEntity user = saveActiveUser("policy@example.com", CURRENT_PASSWORD);

        restTestClient.post()
                .uri(CHANGE_PASSWORD_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user))
                .body(new ChangePasswordRequest(CURRENT_PASSWORD, "too-short"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void rejectsAnIncorrectCurrentPasswordWithoutChangingPasswordOrRevokingRefresh() {
        UserEntity user = saveActiveUser("wrong-current@example.com", CURRENT_PASSWORD);
        RefreshTokenEntity refreshToken = saveRefreshToken(user, "a".repeat(64));

        restTestClient.post()
                .uri(CHANGE_PASSWORD_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user))
                .body(new ChangePasswordRequest("incorrect current password", NEW_PASSWORD))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CURRENT_PASSWORD_INVALID");

        UserEntity persistedUser = userRepository.findById(user.getId()).orElseThrow();
        RefreshTokenEntity persistedRefresh = refreshTokenRepository.findById(refreshToken.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, persistedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, persistedUser.getPasswordHash())).isFalse();
        assertThat(persistedRefresh.getRevokedAt()).isNull();
    }

    @Test
    void changesPasswordRevokesRefreshTokensAndKeepsTheCurrentAccessTokenValid() {
        UserEntity user = saveActiveUser("change@example.com", CURRENT_PASSWORD);
        RefreshTokenEntity refreshToken = saveRefreshToken(user, "b".repeat(64));
        String accessToken = accessToken(user);

        restTestClient.post()
                .uri(CHANGE_PASSWORD_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD))
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");

        UserEntity persistedUser = userRepository.findById(user.getId()).orElseThrow();
        RefreshTokenEntity persistedRefresh = refreshTokenRepository.findById(refreshToken.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, persistedUser.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, persistedUser.getPasswordHash())).isTrue();
        assertThat(persistedRefresh.getRevokedAt()).isNotNull();

        restTestClient.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void documentsChangePasswordInOpenApi() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/me/change-password'].post.responses['204']").exists()
                .jsonPath("$.paths['/api/v1/me/change-password'].post.responses['400']").exists()
                .jsonPath("$.paths['/api/v1/me/change-password'].post.responses['401']").exists()
                .jsonPath("$.paths['/api/v1/me/change-password'].post.responses['403']").exists()
                .jsonPath("$.paths['/api/v1/me/change-password'].post.security[0].bearerAuth").exists();
    }

    private UserEntity saveActiveUser(String email, String rawPassword) {
        Instant createdAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                passwordEncoder.encode(rawPassword),
                "Password Driver",
                UserStatus.ACTIVE,
                UserRole.USER,
                true,
                createdAt,
                createdAt,
                createdAt,
                null
        ));
    }

    private RefreshTokenEntity saveRefreshToken(UserEntity user, String tokenHash) {
        Instant issuedAt = Instant.now();
        return refreshTokenRepository.saveAndFlush(new RefreshTokenEntity(
                UUID.randomUUID(),
                user,
                UUID.randomUUID(),
                null,
                tokenHash,
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS),
                null,
                null,
                "127.0.0.1",
                "integration-test"
        ));
    }

    private String accessToken(UserEntity user) {
        return accessTokenIssuer.issue(user, UUID.randomUUID()).token();
    }
}
