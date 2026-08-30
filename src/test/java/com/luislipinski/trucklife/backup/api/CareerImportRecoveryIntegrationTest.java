package com.luislipinski.trucklife.backup.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.backup.application.CareerImportService;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationRepository;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
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
class CareerImportRecoveryIntegrationTest {

    private static final String IMPORT_PATH = "/api/v1/careers/imports";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired RestTestClient restTestClient;
    @Autowired CareerImportService importService;
    @Autowired CareerImportOperationRepository importRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void clean() {
        importRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void recoversCompletedAtsAssociationWithoutCreatingAnyAdditionalData() {
        UserEntity owner = saveUser("p4-recovery-ats@example.com");
        CareerImportValidationRequest request = freshRequest(CareerGame.ATS, "ats_local_recovery");
        CareerImportResponse created = importService.importCareer(owner.getId(), request);
        long careersBefore = careerRepository.count();
        long operationsBefore = importRepository.count();

        CareerImportResponse recovered = recover(owner, CareerGame.ATS, request.sourceCareerId());

        assertThat(recovered.operationId()).isEqualTo(created.operationId());
        assertThat(recovered.sourceCareerId()).isEqualTo(request.sourceCareerId());
        assertThat(recovered.game()).isEqualTo(CareerGame.ATS);
        assertThat(recovered.sourceVersion()).isEqualTo(12);
        assertThat(recovered.careerId()).isEqualTo(created.careerId());
        assertThat(recovered.persisted()).isTrue();
        assertThat(recovered.idempotentReplay()).isTrue();
        assertThat(recovered.summary()).isEqualTo(created.summary());
        assertThat(careerRepository.count()).isEqualTo(careersBefore);
        assertThat(importRepository.count()).isEqualTo(operationsBefore);
    }

    @Test
    void recoversEts2AssociationSeparatelyFromAts() {
        UserEntity owner = saveUser("p4-recovery-ets2@example.com");
        CareerImportValidationRequest ats = freshRequest(CareerGame.ATS, "shared_local_recovery");
        CareerImportValidationRequest ets2 = freshRequest(CareerGame.ETS2, "shared_local_recovery");
        CareerImportResponse atsCreated = importService.importCareer(owner.getId(), ats);
        CareerImportResponse ets2Created = importService.importCareer(owner.getId(), ets2);

        CareerImportResponse recovered = recover(owner, CareerGame.ETS2, ets2.sourceCareerId());

        assertThat(recovered.game()).isEqualTo(CareerGame.ETS2);
        assertThat(recovered.careerId()).isEqualTo(ets2Created.careerId());
        assertThat(recovered.careerId().equals(atsCreated.careerId())).isFalse();
        assertThat(recovered.summary().currentPayrollMonth()).isEqualTo(1);
        assertThat(careerRepository.count()).isEqualTo(2);
        assertThat(importRepository.count()).isEqualTo(2);
    }

    @Test
    void doesNotExposeAnotherOwnersImportAssociation() {
        UserEntity owner = saveUser("p4-recovery-owner@example.com");
        UserEntity anotherOwner = saveUser("p4-recovery-other@example.com");
        CareerImportValidationRequest request = freshRequest(CareerGame.ATS, "owner_private_local_id");
        importService.importCareer(owner.getId(), request);

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(IMPORT_PATH)
                        .queryParam("game", "ATS")
                        .queryParam("sourceCareerId", request.sourceCareerId())
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken(anotherOwner)))
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_IMPORT_NOT_FOUND");

        assertThat(careerRepository.count()).isEqualTo(1);
        assertThat(importRepository.count()).isEqualTo(1);
    }

    @Test
    void returnsNotFoundForUnknownAssociationAndRequiresAuthentication() {
        UserEntity owner = saveUser("p4-recovery-missing@example.com");

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(IMPORT_PATH)
                        .queryParam("game", "ATS")
                        .queryParam("sourceCareerId", "never_imported")
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_IMPORT_NOT_FOUND");

        restTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(IMPORT_PATH)
                        .queryParam("game", "ATS")
                        .queryParam("sourceCareerId", "never_imported")
                        .build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void exposesRecoveryLookupInOpenApi() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/imports'].get").exists();
    }

    private CareerImportResponse recover(UserEntity owner, CareerGame game, String sourceCareerId) {
        return Objects.requireNonNull(
                restTestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(IMPORT_PATH)
                                .queryParam("game", game.name())
                                .queryParam("sourceCareerId", sourceCareerId)
                                .build())
                        .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(CareerImportResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private CareerImportValidationRequest freshRequest(CareerGame game, String sourceCareerId) {
        Map<String, Object> career = new LinkedHashMap<>();
        career.put("id", sourceCareerId);
        career.put("gameId", game.name().toLowerCase());
        career.put("driverName", game == CareerGame.ATS ? "Recovery ATS Driver" : "Recovery ETS2 Driver");
        career.put("city", game == CareerGame.ATS ? "Phoenix, AZ" : "Berlin");
        career.put("company", "Recovery Logistics");
        career.put("bio", "P4 recovery fixture");
        if (game == CareerGame.ATS) {
            career.put("stateCode", "AZ");
            career.put("baseCurrency", "USD");
            career.put("currency", "USD");
        } else {
            career.put("countryCode", "DE");
            career.put("baseCurrency", "EUR");
            career.put("currency", "EUR");
        }
        career.put("exchangeRate", BigDecimal.ONE);
        career.put("exchangeRateAsOf", "2026-05-01");
        career.put("cityMarketVersion", game == CareerGame.ATS ? "ats-city-market-v1" : "ets2-city-market-v1");
        career.put("cityMarketLabel", "Recovery city reference");
        career.put("cityCostFactor", new BigDecimal("1.0500"));
        career.put("citySalaryFactor", new BigDecimal("1.0300"));
        career.put("currentBalance", new BigDecimal("1200.50"));
        career.put("currentLevel", 1);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("balance", new BigDecimal("1200.50"));
        state.put("emergencyReserve", BigDecimal.ZERO);
        state.put("currentLevel", 1);
        state.put("careerLevel", 1);
        state.put("currentWeek", 1);
        state.put("currentPayrollMonth", 1);
        state.put("payPeriodStartWeek", 1);
        state.put("history", List.of());
        state.put("trips", List.of());
        state.put("closedWeeks", List.of());
        state.put("customExpenses", List.of());
        state.put("incidents", List.of());
        state.put("closedOperationalWeeks", List.of());
        state.put("expenses", Map.of());
        state.put("academy", Map.of("level2", false, "level3", false));
        state.put("dangerousGoodsQualified", false);

        return new CareerImportValidationRequest(
                UUID.randomUUID(),
                sourceCareerId,
                game,
                12,
                career,
                state
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
}
