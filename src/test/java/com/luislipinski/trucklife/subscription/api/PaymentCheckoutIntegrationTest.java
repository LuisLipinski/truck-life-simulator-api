package com.luislipinski.trucklife.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.subscription.application.PaymentOperations;
import com.luislipinski.trucklife.subscription.application.PaymentProvider;
import com.luislipinski.trucklife.subscription.domain.PaymentOrderStatus;
import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import com.luislipinski.trucklife.subscription.persistence.PaymentEventRepository;
import com.luislipinski.trucklife.subscription.persistence.PaymentOrderRepository;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Import(PaymentCheckoutIntegrationTest.FakeProviderConfiguration.class)
class PaymentCheckoutIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired RestTestClient restTestClient;
    @Autowired UserRepository userRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentOrderRepository paymentOrderRepository;
    @Autowired PaymentEventRepository paymentEventRepository;
    @Autowired JwtAccessTokenIssuer tokenIssuer;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PaymentOperations payments;
    @Autowired FakePaymentProvider fakeProvider;

    private ExecutorService executor;

    @BeforeEach
    void clean() {
        paymentEventRepository.deleteAllInBatch();
        paymentOrderRepository.deleteAllInBatch();
        subscriptionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        jdbcTemplate.update("UPDATE plans SET price_cents = NULL WHERE code = 'PREMIUM'");
        fakeProvider.reset();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void closeExecutor() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void refusesCheckoutUntilTheOfficialPremiumPriceExistsWithoutCreatingFinancialState() {
        UserEntity user = saveUser("no-price@example.com");
        String token = accessToken(user);

        restTestClient.post()
                .uri("/api/v1/subscriptions/checkout")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PremiumCheckoutRequest(UUID.randomUUID()))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PREMIUM_PRICE_NOT_CONFIGURED");

        assertThat(subscriptionRepository.count()).isZero();
        assertThat(paymentOrderRepository.count()).isZero();
        assertThat(fakeProvider.calls()).isZero();
    }

    @Test
    void createsAnOwnerScopedPixCheckoutAndReplaysTheSameOperationOnlyOnce() {
        configurePremiumPrice(990);
        UserEntity owner = saveUser("pix-owner@example.com");
        UserEntity other = saveUser("pix-other@example.com");
        String token = accessToken(owner);
        UUID operationId = UUID.randomUUID();

        PremiumCheckoutResponse created = Objects.requireNonNull(
                restTestClient.post()
                        .uri("/api/v1/subscriptions/checkout")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new PremiumCheckoutRequest(operationId))
                        .exchange()
                        .expectStatus().isCreated()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(PremiumCheckoutResponse.class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(created.idempotentReplay()).isFalse();
        assertThat(created.payment().status()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(created.payment().amountCents()).isEqualTo(990);
        assertThat(created.payment().currency()).isEqualTo("BRL");
        assertThat(created.payment().pixCopyPaste()).isEqualTo("000201-pix-copy-paste");
        assertThat(created.payment().pixQrCodeBase64()).isEqualTo("base64-qr-code");
        assertThat(fakeProvider.calls()).isEqualTo(1);
        assertThat(fakeProvider.lastRequest().payerEmail()).isEqualTo("pix-owner@example.com");
        assertThat(fakeProvider.lastRequest().amountCents()).isEqualTo(990);
        assertThat(fakeProvider.lastRequest().operationId()).isEqualTo(operationId);

        PremiumCheckoutResponse replay = Objects.requireNonNull(
                restTestClient.post()
                        .uri("/api/v1/subscriptions/checkout")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new PremiumCheckoutRequest(operationId))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(PremiumCheckoutResponse.class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(replay.payment().id()).isEqualTo(created.payment().id());
        assertThat(fakeProvider.calls()).isEqualTo(1);
        assertThat(paymentOrderRepository.count()).isEqualTo(1);
        assertThat(subscriptionRepository.count()).isEqualTo(1);

        restTestClient.get()
                .uri("/api/v1/payments/" + created.payment().id())
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(created.payment().id().toString())
                .jsonPath("$.status").isEqualTo("PENDING");

        restTestClient.get()
                .uri("/api/v1/payments/" + created.payment().id())
                .headers(headers -> headers.setBearerAuth(accessToken(other)))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PAYMENT_NOT_FOUND");

        restTestClient.get()
                .uri("/api/v1/me/subscription")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.plan").isEqualTo("FREE")
                .jsonPath("$.premium").isEqualTo(false)
                .jsonPath("$.subscriptionStatus").isEqualTo("PENDING_PAYMENT")
                .jsonPath("$.payments.length()").isEqualTo(1);
    }

    @Test
    void serializesConcurrentCheckoutAttemptsForTheSameOperation() {
        configurePremiumPrice(1490);
        UserEntity user = saveUser("pix-concurrent@example.com");
        UUID operationId = UUID.randomUUID();

        CompletableFuture<PaymentOperations.CheckoutResult> first = CompletableFuture.supplyAsync(
                () -> payments.checkoutPremium(user.getId(), user.getEmail(), operationId),
                executor
        );
        CompletableFuture<PaymentOperations.CheckoutResult> second = CompletableFuture.supplyAsync(
                () -> payments.checkoutPremium(user.getId(), user.getEmail(), operationId),
                executor
        );

        PaymentOperations.CheckoutResult firstResult = first.join();
        PaymentOperations.CheckoutResult secondResult = second.join();

        assertThat(firstResult.order().getId()).isEqualTo(secondResult.order().getId());
        assertThat(java.util.List.of(firstResult.idempotentReplay(), secondResult.idempotentReplay()))
                .containsExactlyInAnyOrder(false, true);
        assertThat(fakeProvider.calls()).isEqualTo(1);
        assertThat(paymentOrderRepository.count()).isEqualTo(1);
        assertThat(subscriptionRepository.count()).isEqualTo(1);
    }

    @Test
    void protectsCheckoutPaymentAndSubscriptionEndpointsAndDocumentsThem() {
        restTestClient.post()
                .uri("/api/v1/subscriptions/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PremiumCheckoutRequest(UUID.randomUUID()))
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.get()
                .uri("/api/v1/payments/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.get()
                .uri("/api/v1/me/subscription")
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/subscriptions/checkout'].post.responses['201']").exists()
                .jsonPath("$.paths['/api/v1/payments/{paymentOrderId}'].get").exists()
                .jsonPath("$.paths['/api/v1/me/subscription'].get").exists();
    }

    private void configurePremiumPrice(int priceCents) {
        jdbcTemplate.update(
                "UPDATE plans SET price_cents = ? WHERE code = 'PREMIUM'",
                priceCents
        );
    }

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "encoded-password-not-used",
                email,
                UserStatus.ACTIVE,
                UserRole.USER,
                true,
                now,
                now,
                now,
                null
        ));
    }

    private String accessToken(UserEntity user) {
        return tokenIssuer.issue(user, UUID.randomUUID()).token();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {
        @Bean
        @Primary
        FakePaymentProvider fakePaymentProvider() {
            return new FakePaymentProvider();
        }
    }

    static class FakePaymentProvider implements PaymentProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile PixRequest lastRequest;

        @Override
        public PaymentProviderCode code() {
            return PaymentProviderCode.MERCADO_PAGO;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public PixCheckout createPix(PixRequest request) {
            calls.incrementAndGet();
            lastRequest = request;
            return new PixCheckout(
                    "order-" + request.localPaymentOrderId(),
                    "payment-" + request.localPaymentOrderId(),
                    "000201-pix-copy-paste",
                    "base64-qr-code",
                    "https://example.invalid/pix",
                    Instant.parse("2026-09-22T04:00:00Z")
            );
        }

        int calls() {
            return calls.get();
        }

        PixRequest lastRequest() {
            return lastRequest;
        }

        void reset() {
            calls.set(0);
            lastRequest = null;
        }
    }
}
