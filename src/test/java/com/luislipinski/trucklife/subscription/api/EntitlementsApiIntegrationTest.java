package com.luislipinski.trucklife.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.PlanFeatureCode;
import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import com.luislipinski.trucklife.subscription.persistence.PlanEntity;
import com.luislipinski.trucklife.subscription.persistence.PlanRepository;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionEntity;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class EntitlementsApiIntegrationTest {
    private static final String CAREERS = "/api/v1/careers";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired RestTestClient restTestClient;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired PlanRepository planRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void clean() {
        subscriptionRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void exposesSeededPlansWithoutInventingAPremiumPrice() {
        PlanResponse[] plans = Objects.requireNonNull(
                restTestClient.get()
                        .uri("/api/v1/plans")
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(PlanResponse[].class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(plans).hasSize(2);
        PlanResponse free = java.util.Arrays.stream(plans)
                .filter(plan -> plan.code() == PlanCode.FREE)
                .findFirst().orElseThrow();
        PlanResponse premium = java.util.Arrays.stream(plans)
                .filter(plan -> plan.code() == PlanCode.PREMIUM)
                .findFirst().orElseThrow();

        assertThat(free.priceCents()).isZero();
        assertThat(free.features().get(PlanFeatureCode.MAX_ATS_CAREERS).limit()).isEqualTo(2);
        assertThat(free.features().get(PlanFeatureCode.MAX_ETS2_CAREERS).limit()).isEqualTo(2);
        assertThat(premium.priceCents()).isNull();
        assertThat(premium.features().get(PlanFeatureCode.MAX_ATS_CAREERS).limit()).isNull();
        assertThat(premium.features().get(PlanFeatureCode.BATCH_EXPORT).enabled()).isTrue();
    }

    @Test
    void appliesFreeLimitsPremiumUnlimitedAndExpirationWithoutDeletingCareers() {
        UserEntity user = saveUser("entitlements@example.com");
        String token = tokenIssuer.issue(user, UUID.randomUUID()).token();

        EntitlementsResponse free = entitlements(token);
        assertThat(free.plan()).isEqualTo(PlanCode.FREE);
        assertThat(free.premium()).isFalse();
        assertThat(free.features().get(PlanFeatureCode.MAX_ATS_CAREERS).limit()).isEqualTo(2);
        assertThat(free.features().get(PlanFeatureCode.MAX_ETS2_CAREERS).limit()).isEqualTo(2);

        createCareer(token, "ATS", "AZ", null, "Phoenix, AZ", 1);
        createCareer(token, "ATS", "CA", null, "Los Angeles, CA", 2);
        createCareer(token, "ETS2", null, "DE", "Berlin", 1);
        createCareer(token, "ETS2", null, "FR", "Paris", 2);

        expectCareerLimit(token, "ATS", "TX", null, "Dallas, TX", 3);
        expectCareerLimit(token, "ETS2", null, "PL", "Warsaw", 3);

        PlanEntity premiumPlan = planRepository.findByCodeAndActiveTrue(PlanCode.PREMIUM).orElseThrow();
        Instant now = Instant.now();
        SubscriptionEntity active = subscriptionRepository.saveAndFlush(new SubscriptionEntity(
                UUID.randomUUID(),
                user.getId(),
                premiumPlan.getId(),
                SubscriptionStatus.ACTIVE,
                now.minus(1, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS),
                now.plus(30, ChronoUnit.DAYS),
                false,
                null,
                now.minus(1, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS)
        ));

        EntitlementsResponse premium = entitlements(token);
        assertThat(premium.plan()).isEqualTo(PlanCode.PREMIUM);
        assertThat(premium.premium()).isTrue();
        assertThat(premium.features().get(PlanFeatureCode.MAX_ATS_CAREERS).limit()).isNull();

        createCareer(token, "ATS", "TX", null, "Dallas, TX", 3);
        createCareer(token, "ETS2", null, "PL", "Warsaw", 3);
        assertThat(careerRepository.countByUserIdAndGame(user.getId(), com.luislipinski.trucklife.career.domain.CareerGame.ATS))
                .isEqualTo(3);
        assertThat(careerRepository.countByUserIdAndGame(user.getId(), com.luislipinski.trucklife.career.domain.CareerGame.ETS2))
                .isEqualTo(3);

        subscriptionRepository.deleteById(active.getId());
        subscriptionRepository.flush();
        subscriptionRepository.saveAndFlush(new SubscriptionEntity(
                UUID.randomUUID(),
                user.getId(),
                premiumPlan.getId(),
                SubscriptionStatus.EXPIRED,
                now.minus(31, ChronoUnit.DAYS),
                now.minus(31, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                false,
                null,
                now.minus(31, ChronoUnit.DAYS),
                now
        ));

        EntitlementsResponse expired = entitlements(token);
        assertThat(expired.plan()).isEqualTo(PlanCode.FREE);
        assertThat(expired.premium()).isFalse();
        assertThat(expired.subscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);

        CareerResponse[] atsCareers = listCareers(token, "ATS");
        CareerResponse[] ets2Careers = listCareers(token, "ETS2");
        assertThat(atsCareers).hasSize(3);
        assertThat(ets2Careers).hasSize(3);
        expectCareerLimit(token, "ATS", "WA", null, "Seattle, WA", 4);
        expectCareerLimit(token, "ETS2", null, "IT", "Milano", 4);
    }

    @Test
    void protectsEntitlementsAndDocumentsBothEndpoints() {
        restTestClient.get()
                .uri("/api/v1/me/entitlements")
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/plans'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/me/entitlements'].get.responses['200']").exists();
    }

    private EntitlementsResponse entitlements(String token) {
        return Objects.requireNonNull(
                restTestClient.get()
                        .uri("/api/v1/me/entitlements")
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(EntitlementsResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private CareerResponse createCareer(
            String token,
            String game,
            String state,
            String country,
            String city,
            int number
    ) {
        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(CAREERS)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(careerRequest(game, state, country, city, number))
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(CareerResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private void expectCareerLimit(
            String token,
            String game,
            String state,
            String country,
            String city,
            int number
    ) {
        restTestClient.post()
                .uri(CAREERS)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(careerRequest(game, state, country, city, number))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_LIMIT_REACHED");
    }

    private CareerResponse[] listCareers(String token, String game) {
        return Objects.requireNonNull(
                restTestClient.get()
                        .uri(CAREERS + "?game=" + game)
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(CareerResponse[].class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private Map<String, Object> careerRequest(
            String game,
            String state,
            String country,
            String city,
            int number
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game);
        request.put("driverName", "Entitlement Driver " + number);
        request.put("companyName", "Road Logistics");
        request.put("initialBalance", new BigDecimal("5000.00"));
        request.put("baseCurrency", "ATS".equals(game) ? "USD" : "EUR");
        request.put("displayCurrency", "ATS".equals(game) ? "USD" : "EUR");
        request.put("exchangeRate", new BigDecimal("1.00000000"));
        request.put("exchangeRateAsOf", LocalDate.of(2026, 9, 21));
        if (state != null) request.put("stateCode", state);
        if (country != null) request.put("countryCode", country);
        request.put("baseCity", city);
        request.put("cityMarketVersion", "test-v1");
        request.put("cityMarketLabel", "Test market");
        request.put("cityCostFactor", new BigDecimal("1.0000"));
        request.put("citySalaryFactor", new BigDecimal("1.0000"));
        return request;
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
}
