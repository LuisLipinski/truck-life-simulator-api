package com.luislipinski.trucklife.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.TokenDigests;
import com.luislipinski.trucklife.identity.domain.UserActionTokenPurpose;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.email.VerificationEmailDeliveryPort;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenEntity;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenEntity;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "identity.rate-limit.resend-verification.max-attempts=100",
                "identity.rate-limit.email-verification.max-attempts=100"
        }
)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
@Import(PasswordRecoveryIntegrationTest.EmailTestConfiguration.class)
class PasswordRecoveryIntegrationTest {

    private static final String FORGOT_PATH = "/api/v1/auth/forgot-password";
    private static final String RESET_PATH = "/api/v1/auth/reset-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired private RestTestClient restTestClient;
    @Autowired private UserRepository userRepository;
    @Autowired private UserActionTokenRepository actionTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RecordingEmailDelivery emailDelivery;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAllInBatch();
        actionTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        emailDelivery.clear();
    }

    @Test
    void keepsForgotPasswordNeutralForUnknownEmail() {
        forgot("unknown@example.com", 202);
        assertThat(userRepository.count()).isZero();
        assertThat(actionTokenRepository.count()).isZero();
        assertThat(emailDelivery.resetDeliveries()).isEmpty();
    }

    @Test
    void resetsPasswordWithOneTimeHashedTokenAndRevokesExistingSessions() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String oldPassword = "old secure password 123";
        String newPassword = "new secure password 456";
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                "Driver@Example.com",
                "driver@example.com",
                passwordEncoder.encode(oldPassword),
                "Road Driver",
                UserStatus.ACTIVE,
                UserRole.USER,
                true,
                now.minus(1, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.HOURS)
        ));
        RefreshTokenEntity refresh = refreshTokenRepository.saveAndFlush(new RefreshTokenEntity(
                UUID.randomUUID(),
                user,
                UUID.randomUUID(),
                null,
                TokenDigests.sha256("existing-refresh-token"),
                now,
                now.plus(30, ChronoUnit.DAYS),
                null,
                null,
                "127.0.0.1",
                "test"
        ));

        forgot(" DRIVER@example.com ", 202);

        RecordingEmailDelivery.Delivery delivery = emailDelivery.onlyResetDelivery();
        UserActionTokenEntity token = actionTokenRepository
                .findByTokenHash(TokenDigests.sha256(delivery.rawToken()))
                .orElseThrow();
        assertThat(token.getPurpose()).isEqualTo(UserActionTokenPurpose.PASSWORD_RESET);
        assertThat(token.getTokenHash()).doesNotContain(delivery.rawToken());
        assertThat(token.getExpiresAt()).isEqualTo(token.getCreatedAt().plus(1, ChronoUnit.HOURS));

        reset(delivery.rawToken(), newPassword, 204, null);

        UserEntity changed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(oldPassword, changed.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(newPassword, changed.getPasswordHash())).isTrue();
        assertThat(actionTokenRepository.findById(token.getId()).orElseThrow().getUsedAt()).isNotNull();
        assertThat(refreshTokenRepository.findById(refresh.getId()).orElseThrow().getRevokedAt()).isNotNull();

        reset(delivery.rawToken(), "another valid password 789", 400, "PASSWORD_RESET_TOKEN_INVALID");
    }

    @Test
    void rejectsMalformedResetAndDocumentsBothEndpoints() {
        reset("invalid", "another valid password 123", 400, "VALIDATION_FAILED");

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/auth/forgot-password'].post.responses['202']").exists()
                .jsonPath("$.paths['/api/v1/auth/reset-password'].post.responses['204']").exists();
    }

    private void forgot(String email, int expectedStatus) {
        restTestClient.post()
                .uri(FORGOT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody().isEmpty();
    }

    private void reset(String token, String newPassword, int expectedStatus, String expectedCode) {
        RestTestClient.ResponseSpec response = restTestClient.post()
                .uri(RESET_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token, "newPassword", newPassword))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedCode == null) {
            response.expectBody().isEmpty();
        } else {
            response.expectBody().jsonPath("$.code").isEqualTo(expectedCode);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailTestConfiguration {
        @Bean
        @Primary
        RecordingEmailDelivery recordingEmailDelivery() {
            return new RecordingEmailDelivery();
        }
    }

    static final class RecordingEmailDelivery implements VerificationEmailDeliveryPort {
        private final List<Delivery> resetDeliveries = new ArrayList<>();

        @Override
        public void sendVerificationEmail(String recipient, String displayName, String rawToken, Instant expiresAt) {
        }

        @Override
        public synchronized void sendPasswordResetEmail(String recipient, String displayName, String rawToken, Instant expiresAt) {
            resetDeliveries.add(new Delivery(recipient, displayName, rawToken, expiresAt));
        }

        synchronized Delivery onlyResetDelivery() {
            assertThat(resetDeliveries).hasSize(1);
            return resetDeliveries.getFirst();
        }

        synchronized List<Delivery> resetDeliveries() {
            return List.copyOf(resetDeliveries);
        }

        synchronized void clear() {
            resetDeliveries.clear();
        }

        record Delivery(String recipient, String displayName, String rawToken, Instant expiresAt) {
        }
    }
}
