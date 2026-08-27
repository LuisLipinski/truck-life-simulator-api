package com.luislipinski.trucklife.qualification.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEventRepository;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.qualification.persistence.AcademyProgressRepository;
import com.luislipinski.trucklife.qualification.persistence.QualificationRepository;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
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
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
class ProgressionApiIntegrationTest {
    private static final String CAREERS_PATH = "/api/v1/careers";

    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired private RestTestClient restTestClient;
    @Autowired private UserRepository userRepository;
    @Autowired private CareerRepository careerRepository;
    @Autowired private CareerEventRepository eventRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private AcademyProgressRepository academyRepository;
    @Autowired private QualificationRepository qualificationRepository;
    @Autowired private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        academyRepository.deleteAllInBatch();
        qualificationRepository.deleteAllInBatch();
        tripRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void atsPromotionQualificationAndTripCategoriesUseServerSideProgression() {
        UserEntity owner = saveUser("progression-ats@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "ATS Progression", "USD", "USD", "1.00000000", "AZ", null, "Phoenix, AZ", "5000.00");

        ProgressionResponse initial = getStatus(token, career.id(), CareerGame.ATS);
        assertThat(initial.currentLevel()).isEqualTo((short) 1);
        assertThat(initial.totalDistance()).isEqualByComparingTo("0.00");
        assertThat(initial.promotions().getFirst().requiredDistance()).isEqualByComparingTo("10000.00");
        assertThat(initial.promotions().getFirst().feeAmount()).isEqualByComparingTo("300.00");
        assertThat(initial.dangerousQualification().feeAmount()).isEqualByComparingTo("144.25");

        promoteExpectConflict(token, career.id(), CareerGame.ATS, 1, 1, 2, "PROMOTION_DISTANCE_REQUIRED");
        createTrip(token, career.id(), CareerGame.ATS, "NORMAL", "10000.00", 201);

        restTestClient.post().uri(progressionActionPath(career.id(), CareerGame.ATS, "promotions"))
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", 1, "expectedCurrentLevel", 1, "targetLevel", 2, "academyCompleted", false))
                .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        ProgressionResponse level2 = promote(token, career.id(), CareerGame.ATS, 1, 1, 2);
        assertThat(level2.currentLevel()).isEqualTo((short) 2);
        assertThat(level2.balance()).isEqualByComparingTo("4700.00");
        assertThat(level2.academyProgress()).singleElement().satisfies(progress -> {
            assertThat(progress.operationalWeek()).isEqualTo(1);
            assertThat(progress.distanceAtCompletion()).isEqualByComparingTo("10000.00");
            assertThat(progress.feeAmount()).isEqualByComparingTo("300.00");
            assertThat(progress.policyVersion()).isEqualTo("phase1-qualification-2026-v1");
        });

        createTrip(token, career.id(), CareerGame.ATS, "HAZMAT", "100.00", 400, "TRIP_QUALIFICATION_REQUIRED");
        ProgressionResponse qualified = acquireDangerous(token, career.id(), CareerGame.ATS, 1, 2);
        assertThat(qualified.dangerousGoodsQualified()).isTrue();
        assertThat(qualified.balance()).isEqualByComparingTo("4555.75");
        assertThat(qualified.qualifications()).singleElement().satisfies(item -> {
            assertThat(item.type().name()).isEqualTo("HAZMAT");
            assertThat(item.feeAmount()).isEqualByComparingTo("144.25");
        });

        createTrip(token, career.id(), CareerGame.ATS, "HAZMAT", "40000.00", 201);
        createTrip(token, career.id(), CareerGame.ATS, "DOUBLES", "100.00", 400, "TRIP_LEVEL_REQUIRED");

        ProgressionResponse level3 = promote(token, career.id(), CareerGame.ATS, 1, 2, 3);
        assertThat(level3.currentLevel()).isEqualTo((short) 3);
        assertThat(level3.balance()).isEqualByComparingTo("4496.75");
        assertThat(level3.totalDistance()).isEqualByComparingTo("50000.00");
        createTrip(token, career.id(), CareerGame.ATS, "DOUBLES", "100.00", 201);

        var persisted = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persisted.getCurrentLevel()).isEqualTo((short) 3);
        assertThat(persisted.isDangerousGoodsQualified()).isTrue();
        assertThat(academyRepository.count()).isEqualTo(2);
        assertThat(qualificationRepository.count()).isEqualTo(1);
    }

    @Test
    void ets2UsesCountrySpecificBaseFeesAndFrozenCareerExchangeRate() {
        UserEntity owner = saveUser("progression-gb@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ETS2, "GB Progression", "GBP", "EUR", "1.16000000", null, "GB", "London, Reino Unido", "5000.00");
        createTrip(token, career.id(), CareerGame.ETS2, "NORMAL", "16000.00", 201);

        ProgressionResponse before = getStatus(token, career.id(), CareerGame.ETS2);
        assertThat(before.promotions().getFirst().feeAmount()).isEqualByComparingTo("301.60");
        assertThat(before.dangerousQualification().type().name()).isEqualTo("ADR");
        assertThat(before.dangerousQualification().feeAmount()).isEqualByComparingTo("127.60");

        ProgressionResponse promoted = promote(token, career.id(), CareerGame.ETS2, 1, 1, 2);
        assertThat(promoted.balance()).isEqualByComparingTo("4698.40");
        ProgressionResponse qualified = acquireDangerous(token, career.id(), CareerGame.ETS2, 1, 2);
        assertThat(qualified.balance()).isEqualByComparingTo("4570.80");
        assertThat(qualified.qualifications().getFirst().type().name()).isEqualTo("ADR");
    }

    @Test
    void serializesConcurrentPromotionWithoutDoubleFeeOrDuplicateAcademyProgress() throws Exception {
        UserEntity owner = saveUser("progression-concurrent@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "Concurrent Progression", "USD", "USD", "1.00000000", "AZ", null, "Phoenix, AZ", "5000.00");
        createTrip(token, career.id(), CareerGame.ATS, "NORMAL", "10000.00", 201);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentPromotionStatus(token, career.id(), ready, start));
            Future<Integer> second = executor.submit(() -> concurrentPromotionStatus(token, career.id(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }

        assertThat(academyRepository.count()).isEqualTo(1);
        var persisted = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persisted.getCurrentLevel()).isEqualTo((short) 2);
        assertThat(persisted.getBalance()).isEqualByComparingTo("4700.00");
    }

    @Test
    void enforcesOwnershipPreconditionsBalanceAuthenticationAndOpenApi() {
        UserEntity owner = saveUser("progression-owner@example.com");
        UserEntity intruder = saveUser("progression-intruder@example.com");
        String ownerToken = accessToken(owner);
        String intruderToken = accessToken(intruder);
        CareerResponse career = createCareer(ownerToken, CareerGame.ATS, "Private Progression", "USD", "USD", "1.00000000", "AZ", null, "Phoenix, AZ", "100.00");

        restTestClient.get().uri(progressionPath(career.id(), CareerGame.ATS)).headers(h -> h.setBearerAuth(intruderToken))
                .exchange().expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");
        restTestClient.get().uri(progressionPath(career.id(), CareerGame.ATS)).exchange().expectStatus().isUnauthorized();

        restTestClient.post().uri(progressionActionPath(career.id(), CareerGame.ATS, "dangerous-goods"))
                .headers(h -> h.setBearerAuth(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", 2, "expectedCurrentLevel", 1))
                .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("PROGRESSION_WEEK_CONFLICT");

        createTrip(ownerToken, career.id(), CareerGame.ATS, "NORMAL", "10000.00", 201);
        restTestClient.post().uri(progressionActionPath(career.id(), CareerGame.ATS, "promotions"))
                .headers(h -> h.setBearerAuth(ownerToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", 1, "expectedCurrentLevel", 1, "targetLevel", 2, "academyCompleted", true))
                .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("PROGRESSION_BALANCE_INSUFFICIENT");

        restTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/progression'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/progression/promotions'].post.responses['201']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/progression/dangerous-goods'].post.responses['201']").exists();
    }

    private int concurrentPromotionStatus(String token, UUID careerId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return restTestClient.post().uri(progressionActionPath(careerId, CareerGame.ATS, "promotions"))
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", 1, "expectedCurrentLevel", 1, "targetLevel", 2, "academyCompleted", true))
                .exchange().expectBody().returnResult().getStatus().value();
    }

    private ProgressionResponse getStatus(String token, UUID careerId, CareerGame game) {
        return Objects.requireNonNull(restTestClient.get().uri(progressionPath(careerId, game))
                .headers(h -> h.setBearerAuth(token)).exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(ProgressionResponse.class).returnResult().getResponseBody());
    }

    private ProgressionResponse promote(String token, UUID careerId, CareerGame game, int week, int level, int target) {
        return Objects.requireNonNull(restTestClient.post().uri(progressionActionPath(careerId, game, "promotions"))
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", week, "expectedCurrentLevel", level, "targetLevel", target, "academyCompleted", true))
                .exchange().expectStatus().isCreated().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(ProgressionResponse.class).returnResult().getResponseBody());
    }

    private void promoteExpectConflict(String token, UUID careerId, CareerGame game, int week, int level, int target, String code) {
        restTestClient.post().uri(progressionActionPath(careerId, game, "promotions"))
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", week, "expectedCurrentLevel", level, "targetLevel", target, "academyCompleted", true))
                .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo(code);
    }

    private ProgressionResponse acquireDangerous(String token, UUID careerId, CareerGame game, int week, int level) {
        return Objects.requireNonNull(restTestClient.post().uri(progressionActionPath(careerId, game, "dangerous-goods"))
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", week, "expectedCurrentLevel", level))
                .exchange().expectStatus().isCreated().expectBody(ProgressionResponse.class).returnResult().getResponseBody());
    }

    private void createTrip(String token, UUID careerId, CareerGame game, String category, String distance, int expectedStatus) {
        createTrip(token, careerId, game, category, distance, expectedStatus, null);
    }

    private void createTrip(String token, UUID careerId, CareerGame game, String category, String distance, int expectedStatus, String code) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("departureDay", "monday"); request.put("departureTime", "08:00");
        request.put("arrivalDay", "monday"); request.put("arrivalTime", "12:00");
        request.put("originCity", game == CareerGame.ATS ? "Phoenix, AZ" : "London, Reino Unido");
        request.put("destinationCity", game == CareerGame.ATS ? "Tucson, AZ" : "Manchester, Reino Unido");
        request.put("cargo", "Test cargo"); request.put("type", "LOADED"); request.put("paymentCategory", category);
        request.put("officialDistance", new BigDecimal(distance)); request.put("breakMinutes", 0);
        var exchange = restTestClient.post().uri(CAREERS_PATH + "/" + careerId + "/trips?game=" + game)
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(request).exchange();
        exchange.expectStatus().isEqualTo(expectedStatus);
        if (code != null) exchange.expectBody().jsonPath("$.code").isEqualTo(code);
    }

    private CareerResponse createCareer(String token, CareerGame game, String driverName, String baseCurrency,
                                         String displayCurrency, String exchangeRate, String stateCode, String countryCode,
                                         String baseCity, String balance) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game); request.put("driverName", driverName); request.put("companyName", "Road Logistics");
        request.put("biography", "Progression API integration test"); request.put("initialBalance", new BigDecimal(balance));
        request.put("baseCurrency", baseCurrency); request.put("displayCurrency", displayCurrency);
        request.put("exchangeRate", new BigDecimal(exchangeRate)); request.put("exchangeRateAsOf", "2026-08-26");
        request.put("baseCity", baseCity); request.put("defaultTruckMake", "Test"); request.put("defaultTruckModel", "Truck");
        request.put("cityMarketVersion", "client-test-v1"); request.put("cityMarketLabel", "Client supplied market");
        request.put("cityCostFactor", new BigDecimal("9.0000")); request.put("citySalaryFactor", new BigDecimal("9.0000"));
        if (stateCode != null) request.put("stateCode", stateCode);
        if (countryCode != null) request.put("countryCode", countryCode);
        return Objects.requireNonNull(restTestClient.post().uri(CAREERS_PATH).headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(request).exchange().expectStatus().isCreated()
                .expectBody(CareerResponse.class).returnResult().getResponseBody());
    }

    private String progressionPath(UUID careerId, CareerGame game) {
        return CAREERS_PATH + "/" + careerId + "/progression?game=" + game;
    }

    private String progressionActionPath(UUID careerId, CareerGame game, String action) {
        return CAREERS_PATH + "/" + careerId + "/progression/" + action + "?game=" + game;
    }

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(), email, email.toLowerCase(),
                "encoded-password-not-used-by-this-test", "Progression Owner", UserStatus.ACTIVE, UserRole.USER,
                true, now, now, now, null));
    }

    private String accessToken(UserEntity user) { return accessTokenIssuer.issue(user, UUID.randomUUID()).token(); }
}
