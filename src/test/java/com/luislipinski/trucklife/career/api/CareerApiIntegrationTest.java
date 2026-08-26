package com.luislipinski.trucklife.career.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class CareerApiIntegrationTest {

    private static final String CAREERS_PATH = "/api/v1/careers";
    private static final String ALLOWED_ORIGIN = "https://app.test.truck-life-simulator.local";

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
    private CareerRepository careerRepository;

    @Autowired
    private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void requiresAValidBearerTokenAndExplicitGameContext() {
        restTestClient.get()
                .uri(CAREERS_PATH + "?game=ATS")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");

        restTestClient.get()
                .uri(CAREERS_PATH + "?game=ATS")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_TOKEN_INVALID");

        UserEntity user = saveUser("parameter-owner@example.com");
        restTestClient.get()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken(user)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST_PARAMETER");

        restTestClient.get()
                .uri(CAREERS_PATH + "?game=UNKNOWN")
                .headers(headers -> headers.setBearerAuth(accessToken(user)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST_PARAMETER");
    }

    @Test
    void createsListsAndReadsCareersWithoutMixingAtsAndEts2() {
        UserEntity owner = saveUser("game-owner@example.com");
        String token = accessToken(owner);

        EntityExchangeResult<CareerResponse> atsResult = createResult(
                token,
                createRequest(CareerGame.ATS, "ATS Driver")
        );
        CareerResponse ats = Objects.requireNonNull(atsResult.getResponseBody());
        CareerResponse ets2 = create(token, createRequest(CareerGame.ETS2, "ETS2 Driver"));

        assertThat(atsResult.getResponseHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo(CAREERS_PATH + "/" + ats.id() + "?game=ATS");
        assertThat(atsResult.getResponseHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(ats.game()).isEqualTo(CareerGame.ATS);
        assertThat(ats.stateCode()).isEqualTo("AZ");
        assertThat(ats.countryCode()).isNull();
        assertThat(ats.balance()).isEqualByComparingTo("-250.00");
        assertThat(ats.defaultTruckMake()).isEqualTo("Kenworth");
        assertThat(ats.defaultTruckModel()).isEqualTo("T680");
        assertThat(ats.currentLevel()).isEqualTo((short) 1);
        assertThat(ats.currentOperationalWeek()).isEqualTo(1);
        assertThat(ats.currentPayrollMonth()).isNull();
        assertThat(ats.version()).isZero();
        assertThat(ets2.countryCode()).isEqualTo("DE");
        assertThat(ets2.stateCode()).isNull();
        assertThat(ets2.currentPayrollMonth()).isEqualTo(1);

        CareerResponse[] atsList = restTestClient.get()
                .uri(CAREERS_PATH + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(CareerResponse[].class)
                .returnResult()
                .getResponseBody();
        assertThat(atsList).extracting(CareerResponse::id).containsExactly(ats.id());

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + ats.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.id").isEqualTo(ats.id().toString())
                .jsonPath("$.driverName").isEqualTo("ATS Driver");

        expectCareerNotFound(token, ets2.id(), CareerGame.ATS);
        expectCareerNotFound(token, ats.id(), CareerGame.ETS2);
    }

    @Test
    void hidesAnotherUsersCareerForReadsAndUpdates() {
        UserEntity firstUser = saveUser("first-owner@example.com");
        UserEntity secondUser = saveUser("second-owner@example.com");
        String firstToken = accessToken(firstUser);
        String secondToken = accessToken(secondUser);
        CareerResponse secondCareer = create(
                secondToken,
                createRequest(CareerGame.ATS, "Private Driver")
        );

        restTestClient.get()
                .uri(CAREERS_PATH + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(firstToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);

        expectCareerNotFound(firstToken, secondCareer.id(), CareerGame.ATS);

        restTestClient.patch()
                .uri(CAREERS_PATH + "/" + secondCareer.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(firstToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version", secondCareer.version(),
                        "driverName", "Intruding Driver",
                        "biography", "Unauthorized change"
                ))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + secondCareer.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(secondToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.driverName").isEqualTo("Private Driver")
                .jsonPath("$.version").isEqualTo(0);
    }

    @Test
    void enforcesTwoFreeCareersIndependentlyForEachGame() {
        UserEntity owner = saveUser("free-limit@example.com");
        String token = accessToken(owner);

        create(token, createRequest(CareerGame.ATS, "ATS One"));
        create(token, createRequest(CareerGame.ATS, "ATS Two"));
        expectCareerLimit(token, createRequest(CareerGame.ATS, "ATS Three"));

        create(token, createRequest(CareerGame.ETS2, "ETS2 One"));
        create(token, createRequest(CareerGame.ETS2, "ETS2 Two"));
        expectCareerLimit(token, createRequest(CareerGame.ETS2, "ETS2 Three"));

        assertThat(careerRepository.countByUserIdAndGame(owner.getId(), CareerGame.ATS)).isEqualTo(2);
        assertThat(careerRepository.countByUserIdAndGame(owner.getId(), CareerGame.ETS2)).isEqualTo(2);
    }

    @Test
    void serializesConcurrentCreationAtTheFreeLimit() throws Exception {
        UserEntity owner = saveUser("concurrent-limit@example.com");
        String token = accessToken(owner);
        create(token, createRequest(CareerGame.ATS, "Existing Driver"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentCreateStatus(
                    token,
                    createRequest(CareerGame.ATS, "Concurrent One"),
                    ready,
                    start
            ));
            Future<Integer> second = executor.submit(() -> concurrentCreateStatus(
                    token,
                    createRequest(CareerGame.ATS, "Concurrent Two"),
                    ready,
                    start
            ));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            ));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }

        assertThat(careerRepository.countByUserIdAndGame(owner.getId(), CareerGame.ATS)).isEqualTo(2);
    }

    @Test
    void updatesTheProfileWithOptimisticLockingAndRejectsAStaleCopy() {
        UserEntity owner = saveUser("version-owner@example.com");
        String token = accessToken(owner);
        CareerResponse created = create(token, createRequest(CareerGame.ETS2, "Original Driver"));

        CareerResponse updated = Objects.requireNonNull(restTestClient.patch()
                .uri(CAREERS_PATH + "/" + created.id() + "?game=ETS2")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version", created.version(),
                        "driverName", "Corrected Driver",
                        "biography", "Updated biography"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(CareerResponse.class)
                .returnResult()
                .getResponseBody());

        assertThat(updated.driverName()).isEqualTo("Corrected Driver");
        assertThat(updated.biography()).isEqualTo("Updated biography");
        assertThat(updated.version()).isEqualTo(created.version() + 1);

        restTestClient.patch()
                .uri(CAREERS_PATH + "/" + created.id() + "?game=ETS2")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version", created.version(),
                        "driverName", "Stale Driver",
                        "biography", "Stale biography"
                ))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_VERSION_CONFLICT");

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + created.id() + "?game=ETS2")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.driverName").isEqualTo("Corrected Driver")
                .jsonPath("$.version").isEqualTo(updated.version());
    }

    @Test
    void validatesCreationAndProfileContractsBeforePersistence() {
        UserEntity owner = saveUser("validation-owner@example.com");
        String token = accessToken(owner);
        Map<String, Object> missingLocation = createRequest(CareerGame.ATS, "No State Driver");
        missingLocation.remove("stateCode");

        restTestClient.post()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(missingLocation)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_LOCATION_INVALID");

        Map<String, Object> blankName = createRequest(CareerGame.ATS, " ");
        restTestClient.post()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(blankName)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        CareerResponse career = create(token, createRequest(CareerGame.ATS, "Valid Driver"));
        restTestClient.patch()
                .uri(CAREERS_PATH + "/" + career.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("driverName", "Versionless Driver", "biography", "Invalid request"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        assertThat(careerRepository.countByUserIdAndGame(owner.getId(), CareerGame.ATS)).isEqualTo(1);
    }

    @Test
    void documentsTheCareerApiAndAllowsCorsPatchPreflight() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers'].post.responses['201']").exists()
                .jsonPath("$.paths['/api/v1/careers'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}'].get.responses['404']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}'].patch.responses['409']").exists()
                .jsonPath("$.paths['/api/v1/careers'].post.security[0].bearerAuth").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}'].patch.security[0].bearerAuth").exists();

        restTestClient.options()
                .uri(CAREERS_PATH + "/" + UUID.randomUUID() + "?game=ATS")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ".*PATCH.*");
    }

    private EntityExchangeResult<CareerResponse> createResult(
            String token,
            Map<String, Object> request
    ) {
        return restTestClient.post()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CareerResponse.class)
                .returnResult();
    }

    private CareerResponse create(String token, Map<String, Object> request) {
        return Objects.requireNonNull(createResult(token, request).getResponseBody());
    }

    private void expectCareerLimit(String token, Map<String, Object> request) {
        restTestClient.post()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_LIMIT_REACHED");
    }

    private void expectCareerNotFound(String token, UUID careerId, CareerGame game) {
        restTestClient.get()
                .uri(CAREERS_PATH + "/" + careerId + "?game=" + game)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");
    }

    private int concurrentCreateStatus(
            String token,
            Map<String, Object> request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            return 598;
        }
        return restTestClient.post()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    private Map<String, Object> createRequest(CareerGame game, String driverName) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game);
        request.put("driverName", driverName);
        request.put("companyName", "Road Logistics");
        request.put("biography", "Career API integration test");
        request.put("initialBalance", new BigDecimal("-250.00"));
        request.put("baseCurrency", game == CareerGame.ATS ? "USD" : "EUR");
        request.put("displayCurrency", "BRL");
        request.put("exchangeRate", new BigDecimal("5.25000000"));
        request.put("exchangeRateAsOf", "2026-08-26");
        request.put("baseCity", game == CareerGame.ATS ? "Phoenix, AZ" : "Berlin, Germany");
        request.put("defaultTruckMake", "Kenworth");
        request.put("defaultTruckModel", "T680");
        request.put("cityMarketVersion", "test-v1");
        request.put("cityMarketLabel", "Test market");
        request.put("cityCostFactor", new BigDecimal("1.1000"));
        request.put("citySalaryFactor", new BigDecimal("0.9500"));
        if (game == CareerGame.ATS) {
            request.put("stateCode", "AZ");
        } else {
            request.put("countryCode", "DE");
        }
        return request;
    }

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "encoded-password-not-used-by-this-test",
                "Career Owner",
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
        return accessTokenIssuer.issue(user, UUID.randomUUID()).token();
    }
}
