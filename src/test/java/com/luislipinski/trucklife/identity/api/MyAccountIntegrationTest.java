package com.luislipinski.trucklife.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
class MyAccountIntegrationTest {

    private static final String ME_PATH = "/api/v1/me";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void requiresABearerAccessToken() {
        restTestClient.get()
                .uri(ME_PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED")
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void rejectsAnInvalidAccessToken() {
        restTestClient.get()
                .uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_TOKEN_INVALID")
                .jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void returnsOnlySafeDataFromTheAuthenticatedAccount() {
        UserEntity user = saveUser("driver@example.com", "Main Driver", UserRole.USER, UserStatus.ACTIVE, true);

        EntityExchangeResult<byte[]> result = getMe(accessToken(user), 200);
        String json = new String(result.getResponseBody(), StandardCharsets.UTF_8);

        assertThat(json)
                .contains(user.getId().toString(), "driver@example.com", "Main Driver", "USER", "ACTIVE")
                .doesNotContain("passwordHash", "password_hash", "normalizedEmail", "normalized_email");
    }

    @Test
    void isolatesAccountsByTheUserIdContainedInEachAccessToken() {
        UserEntity first = saveUser("first@example.com", "First Driver", UserRole.USER, UserStatus.ACTIVE, true);
        UserEntity second = saveUser("second@example.com", "Second Driver", UserRole.USER, UserStatus.ACTIVE, true);

        String firstJson = body(getMe(accessToken(first), 200));
        String secondJson = body(getMe(accessToken(second), 200));

        assertThat(firstJson)
                .contains(first.getId().toString(), "first@example.com")
                .doesNotContain(second.getId().toString(), "second@example.com");
        assertThat(secondJson)
                .contains(second.getId().toString(), "second@example.com")
                .doesNotContain(first.getId().toString(), "first@example.com");
    }

    @Test
    void acceptsAdminAsAnAuthorizationRole() {
        UserEntity admin = saveUser("admin@example.com", "Admin Driver", UserRole.ADMIN, UserStatus.ACTIVE, true);

        String json = body(getMe(accessToken(admin), 200));

        assertThat(json).contains("admin@example.com", "ADMIN");
    }

    @Test
    void returnsTheSharedForbiddenProblemForAnAccountThatLostAccess() {
        UserEntity disabled = saveUser(
                "disabled@example.com",
                "Disabled Driver",
                UserRole.USER,
                UserStatus.DISABLED,
                true
        );

        restTestClient.get()
                .uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(disabled))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCOUNT_FORBIDDEN")
                .jsonPath("$.status").isEqualTo(403);
    }

    @Test
    void documentsBearerSecurityAndMyAccountResponsesInOpenApi() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.securitySchemes.bearerAuth.type").isEqualTo("http")
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer")
                .jsonPath("$.paths['/api/v1/me'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/me'].get.responses['401']").exists()
                .jsonPath("$.paths['/api/v1/me'].get.responses['403']").exists()
                .jsonPath("$.paths['/api/v1/me'].get.security[0].bearerAuth").exists();
    }

    private EntityExchangeResult<byte[]> getMe(String accessToken, int expectedStatus) {
        return restTestClient.get()
                .uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody()
                .returnResult();
    }

    private String body(EntityExchangeResult<byte[]> result) {
        return new String(result.getResponseBody(), StandardCharsets.UTF_8);
    }

    private String accessToken(UserEntity user) {
        return accessTokenIssuer.issue(user, UUID.randomUUID()).token();
    }

    private UserEntity saveUser(
            String email,
            String displayName,
            UserRole role,
            UserStatus status,
            boolean emailVerified
    ) {
        Instant createdAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                passwordEncoder.encode("unused valid password"),
                displayName,
                status,
                role,
                emailVerified,
                emailVerified ? createdAt : null,
                createdAt,
                createdAt,
                null
        ));
    }
}
