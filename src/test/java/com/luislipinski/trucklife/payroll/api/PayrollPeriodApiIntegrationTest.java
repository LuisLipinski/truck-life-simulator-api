package com.luislipinski.trucklife.payroll.api;

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
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
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
class PayrollPeriodApiIntegrationTest {

    private static final String CAREERS_PATH = "/api/v1/careers";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired private RestTestClient restTestClient;
    @Autowired private UserRepository userRepository;
    @Autowired private CareerRepository careerRepository;
    @Autowired private CareerEventRepository eventRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private PayrollPeriodRepository payrollPeriodRepository;
    @Autowired private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        payrollPeriodRepository.deleteAllInBatch();
        tripRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void closesEts2CurrentWeekSnapshotsContextAndStartsTheNextWeek() {
        UserEntity owner = saveUser("payroll-owner@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ETS2, "European Payroll Driver");

        PayrollPeriodResponse period = closeWeek(token, career.id(), CareerGame.ETS2, 1);

        assertThat(period.operationalWeek()).isEqualTo(1);
        assertThat(period.payrollMonth()).isEqualTo(1);
        assertThat(period.contextSnapshot())
                .containsEntry("companyName", "Road Logistics")
                .containsEntry("baseCity", "Berlin, Germany")
                .containsEntry("baseCurrency", "EUR")
                .containsEntry("currentLevel", 1);

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "?game=ETS2")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currentOperationalWeek").isEqualTo(2)
                .jsonPath("$.currentPayrollMonth").isEqualTo(1);

        restTestClient.get()
                .uri(periodsPath(career.id(), CareerGame.ETS2))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].operationalWeek").isEqualTo(1);

        restTestClient.post()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips?game=ETS2")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(loadedTrip())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.operationalWeek").isEqualTo(2);
    }

    @Test
    void rejectsConcurrentDuplicateCloseWithoutSkippingTheNextWeek() throws Exception {
        UserEntity owner = saveUser("concurrent-payroll@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ETS2, "Concurrent Payroll Driver");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(
                    () -> concurrentCloseStatus(token, career.id(), 1, ready, start)
            );
            Future<Integer> second = executor.submit(
                    () -> concurrentCloseStatus(token, career.id(), 1, ready, start)
            );

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            ));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }

        assertThat(payrollPeriodRepository.count()).isEqualTo(1);
        assertThat(careerRepository.findById(career.id()).orElseThrow().getCurrentOperationalWeek())
                .isEqualTo(2);
    }

    @Test
    void rejectsStaleWeekAndAtsStandaloneClose() {
        UserEntity owner = saveUser("payroll-conflicts@example.com");
        String token = accessToken(owner);
        CareerResponse ets2 = createCareer(token, CareerGame.ETS2, "Stale Week Driver");
        CareerResponse ats = createCareer(token, CareerGame.ATS, "ATS Weekly Driver");

        closeWeek(token, ets2.id(), CareerGame.ETS2, 1);

        expectConflict(
                token,
                ets2.id(),
                CareerGame.ETS2,
                1,
                "PAYROLL_WEEK_CONFLICT"
        );
        expectConflict(
                token,
                ats.id(),
                CareerGame.ATS,
                1,
                "PAYROLL_ATS_CLOSE_REQUIRES_PAYSLIP"
        );

        assertThat(payrollPeriodRepository.count()).isEqualTo(1);
        assertThat(careerRepository.findById(ats.id()).orElseThrow().getCurrentOperationalWeek())
                .isEqualTo(1);
    }

    @Test
    void limitsEts2OperationalPayrollMonthToFiveClosedWeeks() {
        UserEntity owner = saveUser("payroll-month-limit@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ETS2, "Five Week Driver");

        for (int week = 1; week <= 5; week++) {
            PayrollPeriodResponse period = closeWeek(
                    token,
                    career.id(),
                    CareerGame.ETS2,
                    week
            );
            assertThat(period.operationalWeek()).isEqualTo(week);
            assertThat(period.payrollMonth()).isEqualTo(1);
        }

        expectConflict(
                token,
                career.id(),
                CareerGame.ETS2,
                6,
                "PAYROLL_MONTH_WEEK_LIMIT_REACHED"
        );

        assertThat(payrollPeriodRepository.count()).isEqualTo(5);
        assertThat(careerRepository.findById(career.id()).orElseThrow().getCurrentOperationalWeek())
                .isEqualTo(6);
    }

    @Test
    void isolatesPeriodsByOwnerAndGameAndValidatesTheClosePrecondition() {
        UserEntity owner = saveUser("private-payroll@example.com");
        UserEntity intruder = saveUser("payroll-intruder@example.com");
        String ownerToken = accessToken(owner);
        String intruderToken = accessToken(intruder);
        CareerResponse career = createCareer(
                ownerToken,
                CareerGame.ETS2,
                "Private Payroll Driver"
        );

        restTestClient.post()
                .uri(closePath(career.id(), CareerGame.ETS2))
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", 1))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.get()
                .uri(periodsPath(career.id(), CareerGame.ATS))
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.post()
                .uri(closePath(career.id(), CareerGame.ETS2))
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", 0))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        assertThat(payrollPeriodRepository.count()).isZero();
    }

    @Test
    void requiresAuthenticationAndDocumentsPayrollPeriodEndpoints() {
        UUID careerId = UUID.randomUUID();

        restTestClient.get()
                .uri(periodsPath(careerId, CareerGame.ETS2))
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payroll-periods'].get.responses['200']")
                .exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payroll-periods/close'].post.responses['201']")
                .exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payroll-periods/close'].post.responses['409']")
                .exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payroll-periods/close'].post.security[0].bearerAuth")
                .exists();
    }

    private int concurrentCloseStatus(
            String token,
            UUID careerId,
            int expectedWeek,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return restTestClient.post()
                .uri(closePath(careerId, CareerGame.ETS2))
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", expectedWeek))
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    private PayrollPeriodResponse closeWeek(
            String token,
            UUID careerId,
            CareerGame game,
            int expectedWeek
    ) {
        return Objects.requireNonNull(restTestClient.post()
                .uri(closePath(careerId, game))
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", expectedWeek))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(PayrollPeriodResponse.class)
                .returnResult()
                .getResponseBody());
    }

    private void expectConflict(
            String token,
            UUID careerId,
            CareerGame game,
            int expectedWeek,
            String code
    ) {
        restTestClient.post()
                .uri(closePath(careerId, game))
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", expectedWeek))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo(code);
    }

    private String periodsPath(UUID careerId, CareerGame game) {
        return CAREERS_PATH + "/" + careerId + "/payroll-periods?game=" + game;
    }

    private String closePath(UUID careerId, CareerGame game) {
        return CAREERS_PATH + "/" + careerId + "/payroll-periods/close?game=" + game;
    }

    private Map<String, Object> loadedTrip() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("departureDay", "monday");
        request.put("departureTime", "08:00");
        request.put("arrivalDay", "monday");
        request.put("arrivalTime", "12:00");
        request.put("originCity", "Berlin, Germany");
        request.put("originCompany", "Road Logistics");
        request.put("destinationCity", "Hamburg, Germany");
        request.put("destinationCompany", "Customer Depot");
        request.put("cargo", "Food");
        request.put("type", "Loaded");
        request.put("paymentCategory", "normal");
        request.put("officialDistance", new BigDecimal("285.00"));
        request.put("breakMinutes", 45);
        request.put("truckMake", "MAN");
        request.put("truckModel", "TGX");
        return request;
    }

    private CareerResponse createCareer(String token, CareerGame game, String driverName) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game);
        request.put("driverName", driverName);
        request.put("companyName", "Road Logistics");
        request.put("biography", "Payroll period API integration test");
        request.put("initialBalance", new BigDecimal("5000.00"));
        request.put("baseCurrency", game == CareerGame.ATS ? "USD" : "EUR");
        request.put("displayCurrency", "BRL");
        request.put("exchangeRate", new BigDecimal("5.25000000"));
        request.put("exchangeRateAsOf", "2026-08-26");
        request.put("baseCity", game == CareerGame.ATS ? "Phoenix, AZ" : "Berlin, Germany");
        request.put("defaultTruckMake", game == CareerGame.ATS ? "Kenworth" : "MAN");
        request.put("defaultTruckModel", game == CareerGame.ATS ? "T680" : "TGX");
        request.put("cityMarketVersion", "test-v1");
        request.put("cityMarketLabel", "Test market");
        request.put("cityCostFactor", new BigDecimal("1.1000"));
        request.put("citySalaryFactor", new BigDecimal("0.9500"));
        if (game == CareerGame.ATS) {
            request.put("stateCode", "AZ");
        } else {
            request.put("countryCode", "DE");
        }

        return Objects.requireNonNull(restTestClient.post()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CareerResponse.class)
                .returnResult()
                .getResponseBody());
    }

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "encoded-password-not-used-by-this-test",
                "Payroll Owner",
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
