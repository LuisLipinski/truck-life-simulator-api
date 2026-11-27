package com.luislipinski.trucklife.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.PlanFeatureCode;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionRepository;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "features.premium.restrictions-enabled=false"
)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
class PremiumRestrictionsDisabledIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired RestTestClient restTestClient;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void clean() {
        subscriptionRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void unlocksEveryPremiumFeatureAndCareerLimitForFreeUsersWhenFlagIsDisabled() {
        UserEntity user = saveUser("premium-disabled@example.com");
        String token = tokenIssuer.issue(user, UUID.randomUUID()).token();

        EntitlementsResponse response = Objects.requireNonNull(
                restTestClient.get()
                        .uri("/api/v1/me/entitlements")
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(EntitlementsResponse.class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(response.plan()).isEqualTo(PlanCode.FREE);
        assertThat(response.premium()).isFalse();
        assertThat(response.premiumRestrictionsEnabled()).isFalse();
        for (PlanFeatureCode feature : PlanFeatureCode.values()) {
            assertThat(response.features().get(feature).enabled()).isTrue();
            assertThat(response.features().get(feature).limit()).isNull();
        }

        createCareer(token, "AZ", "Phoenix, AZ", 1);
        createCareer(token, "CA", "Los Angeles, CA", 2);
        createCareer(token, "TX", "Dallas, TX", 3);

        assertThat(careerRepository.countByUserIdAndGame(
                user.getId(),
                com.luislipinski.trucklife.career.domain.CareerGame.ATS
        )).isEqualTo(3);
    }

    @Test
    void exposesThePublicToggleStateWithoutChangingThePlanCatalog() {
        PlanResponse[] plans = Objects.requireNonNull(
                restTestClient.get()
                        .uri("/api/v1/plans")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(PlanResponse[].class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(plans).hasSize(2);
        assertThat(plans).allMatch(plan -> !plan.premiumRestrictionsEnabled());

        PlanResponse free = java.util.Arrays.stream(plans)
                .filter(plan -> plan.code() == PlanCode.FREE)
                .findFirst()
                .orElseThrow();

        assertThat(free.features().get(PlanFeatureCode.MAX_ATS_CAREERS).limit()).isEqualTo(2);
        assertThat(free.features().get(PlanFeatureCode.ADVANCED_CHARTS).enabled()).isFalse();
    }

    private void createCareer(String token, String stateCode, String city, int number) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", "ATS");
        request.put("driverName", "Unlocked Driver " + number);
        request.put("companyName", "Open Roads");
        request.put("initialBalance", new BigDecimal("5000.00"));
        request.put("baseCurrency", "USD");
        request.put("displayCurrency", "USD");
        request.put("exchangeRate", new BigDecimal("1.00000000"));
        request.put("exchangeRateAsOf", LocalDate.of(2026, 11, 27));
        request.put("stateCode", stateCode);
        request.put("baseCity", city);
        request.put("cityMarketVersion", "test-v1");
        request.put("cityMarketLabel", "Test market");
        request.put("cityCostFactor", new BigDecimal("1.0000"));
        request.put("citySalaryFactor", new BigDecimal("1.0000"));

        restTestClient.post()
                .uri("/api/v1/careers")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated();
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
