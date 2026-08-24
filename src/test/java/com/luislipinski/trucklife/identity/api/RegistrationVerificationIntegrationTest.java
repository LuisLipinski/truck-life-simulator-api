package com.luislipinski.trucklife.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.TokenDigests;
import com.luislipinski.trucklife.identity.domain.UserActionTokenPurpose;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.email.VerificationEmailDeliveryPort;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenEntity;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
                "identity.rate-limit.registration.max-attempts=100",
                "identity.rate-limit.email-verification.max-attempts=5",
                "identity.rate-limit.resend-verification.max-attempts=2"
        }
)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
@Import(RegistrationVerificationIntegrationTest.EmailTestConfiguration.class)
class RegistrationVerificationIntegrationTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String VERIFY_PATH = "/api/v1/auth/verify-email";
    private static final String RESEND_PATH = "/api/v1/auth/resend-verification";

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
    private UserActionTokenRepository actionTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordingVerificationEmailDelivery emailDelivery;

    @BeforeEach
    void cleanPersistenceAndCapturedMessages() {
        actionTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        emailDelivery.clear();
    }

    @Test
    void registersPendingAccountWithNormalizedEmailAndOnlyHashedSecrets() {
        String rawPassword = "a secure 🚚 password";

        register(" Driver@Example.COM ", "  Road Driver  ", rawPassword);

        RecordingVerificationEmailDelivery.Delivery delivery = emailDelivery.onlyDelivery();
        UserEntity user = userRepository.findByNormalizedEmail("driver@example.com").orElseThrow();
        UserActionTokenEntity token = actionTokenRepository
                .findByTokenHash(TokenDigests.sha256(delivery.rawToken()))
                .orElseThrow();

        assertThat(user.getEmail()).isEqualTo("Driver@Example.COM");
        assertThat(user.getDisplayName()).isEqualTo("Road Driver");
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getPasswordHash())
                .startsWith("{argon2id-v1}$argon2id$")
                .doesNotContain(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, user.getPasswordHash())).isTrue();
        assertThat(delivery.rawToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(token.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(token.getTokenHash()).isNotEqualTo(delivery.rawToken());
        assertThat(token.getExpiresAt()).isEqualTo(token.getCreatedAt().plus(24, ChronoUnit.HOURS));

        register("driver@example.com", "Another Driver", "another secure password", 202);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(actionTokenRepository.count()).isEqualTo(1);
        assertThat(emailDelivery.size()).isEqualTo(1);
    }

    @Test
    void keepsConcurrentRegistrationsForTheSameNormalizedEmailNeutralAndUnique()
            throws Exception {
        List<Callable<Void>> attempts = List.of(
                () -> {
                    register(
                            "Concurrent@Example.com",
                            "Concurrent Driver",
                            "a valid concurrent password"
                    );
                    return null;
                },
                () -> {
                    register(
                            " concurrent@example.com ",
                            "Concurrent Driver",
                            "another concurrent password"
                    );
                    return null;
                }
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (Future<Void> result : executor.invokeAll(attempts)) {
                result.get();
            }
        }

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(actionTokenRepository.count()).isEqualTo(1);
        assertThat(emailDelivery.size()).isEqualTo(1);
    }

    @Test
    void verifiesEmailOnceAndTreatsARepeatedUseAsSuccess() {
        register("verify@example.com", "Verify Driver", "a valid registration password");
        String rawToken = emailDelivery.onlyDelivery().rawToken();

        verify(rawToken, 204, null);
        verify(rawToken, 204, null);

        UserEntity user = userRepository.findByNormalizedEmail("verify@example.com").orElseThrow();
        UserActionTokenEntity token = actionTokenRepository
                .findByTokenHash(TokenDigests.sha256(rawToken))
                .orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void resendReplacesTheActiveTokenAndRemainsNeutralAfterVerification() {
        register("resend@example.com", "Resend Driver", "a valid registration password");
        String firstToken = emailDelivery.onlyDelivery().rawToken();

        resend(" RESEND@example.com ", 202, null);

        assertThat(emailDelivery.size()).isEqualTo(2);
        String replacementToken = emailDelivery.lastDelivery().rawToken();
        assertThat(replacementToken).isNotEqualTo(firstToken);
        assertThat(actionTokenRepository.findByTokenHash(TokenDigests.sha256(firstToken)))
                .get()
                .extracting(UserActionTokenEntity::getUsedAt)
                .isNotNull();

        verify(firstToken, 400, "EMAIL_VERIFICATION_TOKEN_INVALID");
        verify(replacementToken, 204, null);
        resend("resend@example.com", 202, null);

        assertThat(emailDelivery.size()).isEqualTo(2);
    }

    @Test
    void rejectsExpiredAndMalformedTokensWithoutChangingTheAccount() {
        String expiredRawToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(repeatedByteArray((byte) 7));
        Instant now = Instant.now();
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                "expired@example.com",
                "expired@example.com",
                "{argon2id-v1}test-hash",
                "Expired Driver",
                UserStatus.PENDING_VERIFICATION,
                UserRole.USER,
                false,
                null,
                now.minus(2, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS),
                null
        ));
        actionTokenRepository.saveAndFlush(new UserActionTokenEntity(
                UUID.randomUUID(),
                user,
                UserActionTokenPurpose.EMAIL_VERIFICATION,
                TokenDigests.sha256(expiredRawToken),
                now.minus(1, ChronoUnit.DAYS),
                null,
                now.minus(2, ChronoUnit.DAYS)
        ));

        verify(expiredRawToken, 400, "EMAIL_VERIFICATION_TOKEN_EXPIRED");
        verify("not-a-token", 400, "VALIDATION_FAILED");

        UserEntity unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchanged.isEmailVerified()).isFalse();
        assertThat(unchanged.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    void validatesRegistrationFieldsWithoutPersistingOrDeliveringSecrets() {
        restTestClient.post()
                .uri(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "not-an-email",
                        "displayName", " x ",
                        "password", "too short"
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.violations.length()").isEqualTo(3);

        assertThat(userRepository.count()).isZero();
        assertThat(actionTokenRepository.count()).isZero();
        assertThat(emailDelivery.size()).isZero();
    }

    @Test
    void rateLimitsNeutralResendRequestsAndReturnsRetryAfter() {
        resend("unknown-rate-limit@example.com", 202, null);
        resend("unknown-rate-limit@example.com", 202, null);
        resend("unknown-rate-limit@example.com", 429, "RATE_LIMIT_EXCEEDED");
    }

    @Test
    void documentsAllThreeOperationsInOpenApi() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/auth/register'].post.responses['202']").exists()
                .jsonPath("$.paths['/api/v1/auth/verify-email'].post.responses['204']").exists()
                .jsonPath("$.paths['/api/v1/auth/resend-verification'].post.responses['202']").exists();
    }

    private void register(String email, String displayName, String password) {
        register(email, displayName, password, 202);
    }

    private void register(String email, String displayName, String password, int expectedStatus) {
        restTestClient.post()
                .uri(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "displayName", displayName,
                        "password", password
                ))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody().isEmpty();
    }

    private void verify(String token, int expectedStatus, String expectedCode) {
        RestTestClient.ResponseSpec response = restTestClient.post()
                .uri(VERIFY_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedCode == null) {
            response.expectBody().isEmpty();
        } else {
            response.expectBody().jsonPath("$.code").isEqualTo(expectedCode);
        }
    }

    private void resend(String email, int expectedStatus, String expectedCode) {
        RestTestClient.ResponseSpec response = restTestClient.post()
                .uri(RESEND_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedCode == null) {
            response.expectBody().isEmpty();
        } else {
            response.expectHeader().exists("Retry-After")
                    .expectBody()
                    .jsonPath("$.code").isEqualTo(expectedCode);
        }
    }

    private byte[] repeatedByteArray(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmailTestConfiguration {

        @Bean
        @Primary
        RecordingVerificationEmailDelivery recordingVerificationEmailDelivery() {
            return new RecordingVerificationEmailDelivery();
        }
    }

    static final class RecordingVerificationEmailDelivery
            implements VerificationEmailDeliveryPort {

        private final List<Delivery> deliveries = new ArrayList<>();

        @Override
        public synchronized void sendVerificationEmail(
                String recipient,
                String displayName,
                String rawToken,
                Instant expiresAt
        ) {
            deliveries.add(new Delivery(recipient, displayName, rawToken, expiresAt));
        }

        synchronized Delivery onlyDelivery() {
            assertThat(deliveries).hasSize(1);
            return deliveries.getFirst();
        }

        synchronized Delivery lastDelivery() {
            return deliveries.getLast();
        }

        synchronized int size() {
            return deliveries.size();
        }

        synchronized void clear() {
            deliveries.clear();
        }

        record Delivery(String recipient, String displayName, String rawToken, Instant expiresAt) {
        }
    }
}
