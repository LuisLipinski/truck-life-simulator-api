package com.luislipinski.trucklife.backup.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.backup.application.CareerImportService;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationRepository;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
class CareerImportOrchestrationIntegrationTest {

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

    private ExecutorService executor;

    @BeforeEach
    void clean() {
        importRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void closeExecutor() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void importsFreshAtsCareerAndReplaysSameOperationWithoutDuplicatingData() {
        UserEntity owner = saveUser("p4-orchestration-owner@example.com");
        CareerImportValidationRequest request = freshAtsRequest(UUID.randomUUID(), "career_local_fresh_1");

        CareerImportResponse created = Objects.requireNonNull(
                restTestClient.post()
                        .uri(IMPORT_PATH)
                        .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(CareerImportResponse.class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(created.persisted()).isTrue();
        assertThat(created.idempotentReplay()).isFalse();
        assertThat(created.operationId()).isEqualTo(request.operationId());
        assertThat(created.sourceCareerId()).isEqualTo(request.sourceCareerId());
        assertThat(created.careerId().toString()).isNotEqualTo(request.sourceCareerId());
        assertThat(created.summary().baseCurrency()).isEqualTo("USD");
        assertThat(created.summary().displayCurrency()).isEqualTo("USD");

        CareerEntity stored = careerRepository.findById(created.careerId()).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(owner.getId());
        assertThat(stored.getGame()).isEqualTo(CareerGame.ATS);
        assertThat(stored.getDriverName()).isEqualTo("Fresh ATS Driver");
        assertThat(stored.getBalance()).isEqualByComparingTo("1200.50");
        assertThat(stored.getCurrentLevel()).isEqualTo((short) 1);
        assertThat(stored.getCurrentOperationalWeek()).isEqualTo(1);
        assertThat(stored.getCurrentPayrollMonth()).isNull();
        assertThat(stored.getStateCode()).isEqualTo("AZ");
        assertThat(stored.getCountryCode()).isNull();
        assertThat(stored.getExchangeRateAsOf()).hasToString("2026-05-01");

        CareerImportResponse replayed = Objects.requireNonNull(
                restTestClient.post()
                        .uri(IMPORT_PATH)
                        .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(CareerImportResponse.class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(replayed.idempotentReplay()).isTrue();
        assertThat(replayed.careerId()).isEqualTo(created.careerId());
        assertThat(replayed.summary()).isEqualTo(created.summary());
        assertThat(careerRepository.count()).isEqualTo(1);
        assertThat(importRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsOperationReuseWithDifferentSnapshotAndSameSourceWithAnotherOperation() {
        UserEntity owner = saveUser("p4-orchestration-conflict@example.com");
        UUID operationId = UUID.randomUUID();
        CareerImportValidationRequest request = freshAtsRequest(operationId, "career_local_conflict");
        postCreated(owner, request);

        Map<String, Object> changedCareer = new LinkedHashMap<>(request.career());
        Map<String, Object> changedState = new LinkedHashMap<>(request.state());
        changedCareer.put("currentBalance", new BigDecimal("1300.50"));
        changedState.put("balance", new BigDecimal("1300.50"));
        CareerImportValidationRequest changed = new CareerImportValidationRequest(
                operationId,
                request.sourceCareerId(),
                request.game(),
                request.sourceVersion(),
                changedCareer,
                changedState
        );

        restTestClient.post()
                .uri(IMPORT_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(changed)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_IMPORT_IDEMPOTENCY_CONFLICT");

        CareerImportValidationRequest anotherOperation = new CareerImportValidationRequest(
                UUID.randomUUID(),
                request.sourceCareerId(),
                request.game(),
                request.sourceVersion(),
                request.career(),
                request.state()
        );
        restTestClient.post()
                .uri(IMPORT_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(anotherOperation)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_IMPORT_ALREADY_EXISTS");

        assertThat(careerRepository.count()).isEqualTo(1);
        assertThat(importRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsHistoricalAggregateWithoutLeavingPartialCareerOrOperation() {
        UserEntity owner = saveUser("p4-orchestration-aggregate@example.com");
        CareerImportValidationRequest base = freshAtsRequest(UUID.randomUUID(), "career_local_history");
        Map<String, Object> state = new LinkedHashMap<>(base.state());
        state.put("trips", List.of(Map.of("week", 1, "distance", 180)));
        CareerImportValidationRequest request = new CareerImportValidationRequest(
                base.operationId(),
                base.sourceCareerId(),
                base.game(),
                base.sourceVersion(),
                base.career(),
                state
        );

        restTestClient.post()
                .uri(IMPORT_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_IMPORT_AGGREGATE_PENDING");

        assertThat(careerRepository.count()).isZero();
        assertThat(importRepository.count()).isZero();
    }

    @Test
    void scopesSameLocalCareerIdentityToDifferentOwners() {
        UserEntity first = saveUser("p4-orchestration-first@example.com");
        UserEntity second = saveUser("p4-orchestration-second@example.com");
        String sourceCareerId = "career_shared_local_id";

        CareerImportResponse firstImport = postCreated(
                first,
                freshAtsRequest(UUID.randomUUID(), sourceCareerId)
        );
        CareerImportResponse secondImport = postCreated(
                second,
                freshAtsRequest(UUID.randomUUID(), sourceCareerId)
        );

        assertThat(firstImport.careerId()).isNotEqualTo(secondImport.careerId());
        assertThat(careerRepository.count()).isEqualTo(2);
        assertThat(importRepository.count()).isEqualTo(2);
    }

    @Test
    void serializesConcurrentAttemptsOfTheSameOperation() {
        UserEntity owner = saveUser("p4-orchestration-race@example.com");
        CareerImportValidationRequest request = freshAtsRequest(UUID.randomUUID(), "career_local_race");

        CompletableFuture<CareerImportResponse> first = CompletableFuture.supplyAsync(
                () -> importService.importCareer(owner.getId(), request), executor
        );
        CompletableFuture<CareerImportResponse> second = CompletableFuture.supplyAsync(
                () -> importService.importCareer(owner.getId(), request), executor
        );

        List<CareerImportResponse> responses = List.of(first.join(), second.join());
        assertThat(responses).extracting(CareerImportResponse::careerId).containsOnly(responses.getFirst().careerId());
        assertThat(responses).extracting(CareerImportResponse::idempotentReplay).containsExactlyInAnyOrder(false, true);
        assertThat(careerRepository.count()).isEqualTo(1);
        assertThat(importRepository.count()).isEqualTo(1);
    }

    @Test
    void exposesImportEndpointInOpenApi() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/imports'].post").exists();
    }

    private CareerImportResponse postCreated(UserEntity owner, CareerImportValidationRequest request) {
        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(IMPORT_PATH)
                        .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(CareerImportResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private CareerImportValidationRequest freshAtsRequest(UUID operationId, String sourceCareerId) {
        Map<String, Object> career = new LinkedHashMap<>();
        career.put("id", sourceCareerId);
        career.put("gameId", "ats");
        career.put("driverName", "Fresh ATS Driver");
        career.put("city", "Phoenix, AZ");
        career.put("company", "Fresh Logistics");
        career.put("bio", "Imported local career");
        career.put("stateCode", "AZ");
        career.put("baseCurrency", "USD");
        career.put("currency", "USD");
        career.put("exchangeRate", new BigDecimal("1.0"));
        career.put("exchangeRateAsOf", "2026-05-01");
        career.put("cityMarketVersion", "ats-city-market-v1");
        career.put("cityMarketLabel", "Phoenix reference");
        career.put("cityCostFactor", new BigDecimal("1.0500"));
        career.put("citySalaryFactor", new BigDecimal("1.0300"));
        career.put("defaultTruckMake", "Kenworth");
        career.put("defaultTruckModel", "T680");
        career.put("currentBalance", new BigDecimal("1200.50"));
        career.put("currentLevel", 1);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("balance", new BigDecimal("1200.50"));
        state.put("emergencyReserve", BigDecimal.ZERO);
        state.put("currentLevel", 1);
        state.put("careerLevel", 1);
        state.put("currentWeek", 1);
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
                operationId,
                sourceCareerId,
                CareerGame.ATS,
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
