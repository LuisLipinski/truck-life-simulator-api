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
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodEntity;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
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
class PayslipApiIntegrationTest {

    private static final String CAREERS_PATH = "/api/v1/careers";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired private RestTestClient restTestClient;
    @Autowired private UserRepository userRepository;
    @Autowired private CareerRepository careerRepository;
    @Autowired private CareerEventRepository eventRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private PayrollPeriodRepository payrollPeriodRepository;
    @Autowired private PayslipRepository payslipRepository;
    @Autowired private PayslipLineRepository payslipLineRepository;
    @Autowired private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        payslipLineRepository.deleteAllInBatch();
        payslipRepository.deleteAllInBatch();
        payrollPeriodRepository.deleteAllInBatch();
        tripRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void generatesAtsWeeklyPayslipClosesWeekAndCreditsBalanceAtomically() {
        UserEntity owner = saveUser("ats-payslip@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "ATS Payroll Driver");
        createTrip(token, career.id(), CareerGame.ATS, overnightTrip("100.00"));
        PayslipResponse payslip = generate(token, career.id(), CareerGame.ATS, Map.of("expectedOperationalWeek", 1));

        assertThat(payslip.operationalWeek()).isEqualTo(1);
        assertThat(payslip.payrollMonth()).isNull();
        assertThat(payslip.grossAmount()).isEqualByComparingTo("1159.20");
        assertThat(payslip.benefitsAmount()).isEqualByComparingTo("36.00");
        assertThat(payslip.perDiemAmount()).isEqualByComparingTo("160.00");
        assertThat(payslip.overrunMinutes()).isEqualTo(480);
        assertThat(payslip.contextSnapshot())
                .containsEntry("policyVersion", "phase1-payroll-2026-v2")
                .containsEntry("cityMarketVersion", "1")
                .containsEntry("cityMarketKey", "major")
                .containsEntry("cityMarketKnown", true)
                .containsEntry("incidentDeductionsIncluded", false);
        assertThat(payslip.contextSnapshot()).containsKey("dailyWorkBreakdown");
        assertThat(new BigDecimal(String.valueOf(payslip.contextSnapshot().get("citySalaryFactor"))))
                .isEqualByComparingTo("1.05");
        assertThat(payslip.lines()).extracting(PayslipResponse.LineResponse::code)
                .contains("BASE_SALARY", "ROUTE_OVERRUN", "PER_DIEM", "BENEFITS");

        var persistedCareer = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persistedCareer.getCurrentOperationalWeek()).isEqualTo(2);
        assertThat(persistedCareer.getBalance()).isEqualByComparingTo(new BigDecimal("5000.00").add(payslip.depositAmount()));
        List<PayrollPeriodEntity> periods = payrollPeriodRepository.findAllByCareerIdOrderByOperationalWeekAsc(career.id());
        assertThat(periods).singleElement().satisfies(period -> {
            assertThat(period.getOperationalWeek()).isEqualTo(1);
            assertThat(period.getPayrollMonth()).isNull();
            assertThat(period.getPayslipId()).isEqualTo(payslip.id());
        });
        restTestClient.get().uri(payslipPath(career.id(), CareerGame.ATS, payslip.id()))
                .headers(headers -> headers.setBearerAuth(token)).exchange().expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store").expectBody()
                .jsonPath("$.id").isEqualTo(payslip.id().toString());
        restTestClient.get().uri(payslipsPath(career.id(), CareerGame.ATS))
                .headers(headers -> headers.setBearerAuth(token)).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void persistsPreferencesPreviewsAndUsesThemInAtsGeneration() {
        UserEntity owner = saveUser("ats-prefs@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "ATS Preferences Driver");
        String settingsPath = payslipsBase(career.id()) + "/settings?game=ATS";
        String previewPath = payslipsBase(career.id()) + "/preview?game=ATS";

        restTestClient.patch().uri(settingsPath).headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of(
                        "expectedOperationalWeek", 1,
                        "level1Gross", new BigDecimal("1000.00"),
                        "routeOverrunRate", new BigDecimal("30.00"),
                        "benefits", new BigDecimal("50.00"),
                        "perDiemRate", new BigDecimal("90.00")
                )).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.level1Gross").isEqualTo(1000.0)
                .jsonPath("$.routeOverrunRate").isEqualTo(30.0)
                .jsonPath("$.benefits").isEqualTo(50.0)
                .jsonPath("$.perDiemRate").isEqualTo(90.0);

        restTestClient.get().uri(previewPath).headers(headers -> headers.setBearerAuth(token))
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.ready").isEqualTo(true)
                .jsonPath("$.grossAmount").isEqualTo(1000.0)
                .jsonPath("$.benefitsAmount").isEqualTo(50.0)
                .jsonPath("$.taxAmount").isNumber()
                .jsonPath("$.depositAmount").isNumber();

        PayslipResponse payslip = generate(token, career.id(), CareerGame.ATS, Map.of("expectedOperationalWeek", 1));
        assertThat(payslip.grossAmount()).isEqualByComparingTo("1000.00");
        assertThat(payslip.benefitsAmount()).isEqualByComparingTo("50.00");
        assertThat(payslip.contextSnapshot()).containsKey("payrollLevel1GrossOverride");
        assertThat(new BigDecimal(String.valueOf(
                payslip.contextSnapshot().get("payrollLevel1GrossOverride"))))
                .isEqualByComparingTo("1000.00");
        var persisted = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persisted.getPayrollLevel1GrossOverride()).isEqualByComparingTo("1000.00");
    }

    @Test
    void serializesConcurrentAtsPayslipGenerationWithoutDoubleCreditOrWeekSkip() throws Exception {
        UserEntity owner = saveUser("ats-payslip-concurrent@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "Concurrent ATS Driver");
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentGenerateStatus(token, career.id(), ready, start));
            Future<Integer> second = executor.submit(() -> concurrentGenerateStatus(token, career.id(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(payslipRepository.count()).isEqualTo(1);
        assertThat(payrollPeriodRepository.count()).isEqualTo(1);
        assertThat(careerRepository.findById(career.id()).orElseThrow().getCurrentOperationalWeek()).isEqualTo(2);
    }

    @Test
    void generatesEts2MonthlyPayslipOnlyAfterFourClosedWeeksAndAdvancesOperationalMonth() {
        UserEntity owner = saveUser("ets2-payslip@example.com"); String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ETS2, "ETS2 Payroll Driver");
        closeWeek(token, career.id(), 1); closeWeek(token, career.id(), 2); closeWeek(token, career.id(), 3);
        restTestClient.post().uri(payslipsPath(career.id(), CareerGame.ETS2)).headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedPayrollMonth", 1)).exchange()
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("PAYSLIP_ETS2_PERIODS_INSUFFICIENT");
        closeWeek(token, career.id(), 4);
        PayslipResponse payslip = generate(token, career.id(), CareerGame.ETS2, Map.of("expectedPayrollMonth", 1));
        assertThat(payslip.operationalWeek()).isNull(); assertThat(payslip.payrollMonth()).isEqualTo(1);
        assertThat(payslip.startOperationalWeek()).isEqualTo(1); assertThat(payslip.endOperationalWeek()).isEqualTo(4);
        assertThat(payslip.grossAmount()).isEqualByComparingTo("2800.00");
        assertThat(payslip.contextSnapshot()).containsEntry("sourceOperationalWeeks", List.of(1, 2, 3, 4));
        var persistedCareer = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persistedCareer.getCurrentOperationalWeek()).isEqualTo(5);
        assertThat(persistedCareer.getCurrentPayrollMonth()).isEqualTo(2);
        assertThat(payrollPeriodRepository.findAllByCareerIdOrderByOperationalWeekAsc(career.id()))
                .allSatisfy(period -> assertThat(period.getPayslipId()).isEqualTo(payslip.id()));
        restTestClient.post().uri(payslipsPath(career.id(), CareerGame.ETS2)).headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedPayrollMonth", 1)).exchange()
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("PAYSLIP_MONTH_CONFLICT");
    }

    @Test
    void enforcesOwnershipAuthenticationValidationAndOpenApiContract() {
        UserEntity owner = saveUser("payslip-owner@example.com"); UserEntity intruder = saveUser("payslip-intruder@example.com");
        String ownerToken = accessToken(owner); String intruderToken = accessToken(intruder);
        CareerResponse career = createCareer(ownerToken, CareerGame.ATS, "Private Payslip Driver");
        restTestClient.get().uri(payslipsPath(career.id(), CareerGame.ATS)).headers(headers -> headers.setBearerAuth(intruderToken))
                .exchange().expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");
        restTestClient.get().uri(payslipsPath(career.id(), CareerGame.ATS)).exchange().expectStatus().isUnauthorized();
        restTestClient.post().uri(payslipsPath(career.id(), CareerGame.ATS)).headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek", 0)).exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED");
        restTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payslips'].post.responses['201']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payslips'].post.responses['409']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payslips/{payslipId}'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payslips/settings'].get").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payslips/settings'].patch").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/payslips/preview'].get").exists();
    }

    private int concurrentGenerateStatus(String token, UUID careerId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return restTestClient.post().uri(payslipsPath(careerId, CareerGame.ATS)).headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("expectedOperationalWeek", 1)).exchange()
                .expectBody().returnResult().getStatus().value();
    }

    private PayslipResponse generate(String token, UUID careerId, CareerGame game, Map<String, Object> body) {
        return Objects.requireNonNull(restTestClient.post().uri(payslipsPath(careerId, game))
                .headers(headers -> headers.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange().expectStatus().isCreated().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(PayslipResponse.class).returnResult().getResponseBody());
    }

    private void closeWeek(String token, UUID careerId, int expectedWeek) {
        restTestClient.post().uri(CAREERS_PATH + "/" + careerId + "/payroll-periods/close?game=ETS2")
                .headers(headers -> headers.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", expectedWeek)).exchange().expectStatus().isCreated();
    }
    private void createTrip(String token, UUID careerId, CareerGame game, Map<String, Object> body) {
        restTestClient.post().uri(CAREERS_PATH + "/" + careerId + "/trips?game=" + game)
                .headers(headers -> headers.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(body).exchange().expectStatus().isCreated();
    }
    private String payslipsBase(UUID careerId) { return CAREERS_PATH + "/" + careerId + "/payslips"; }
    private String payslipsPath(UUID careerId, CareerGame game) { return payslipsBase(careerId) + "?game=" + game; }
    private String payslipPath(UUID careerId, CareerGame game, UUID payslipId) { return payslipsBase(careerId) + "/" + payslipId + "?game=" + game; }

    private Map<String, Object> overnightTrip(String distance) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("departureDay", "monday"); request.put("departureTime", "08:00");
        request.put("arrivalDay", "tuesday"); request.put("arrivalTime", "08:00");
        request.put("originCity", "Phoenix, AZ"); request.put("originCompany", "Road Logistics");
        request.put("destinationCity", "Tucson, AZ"); request.put("destinationCompany", "Customer Depot");
        request.put("cargo", "Food"); request.put("type", "Loaded"); request.put("paymentCategory", "normal");
        request.put("officialDistance", new BigDecimal(distance)); request.put("breakMinutes", 0); return request;
    }

    private CareerResponse createCareer(String token, CareerGame game, String driverName) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game); request.put("driverName", driverName); request.put("companyName", "Road Logistics");
        request.put("biography", "Payslip API integration test"); request.put("initialBalance", new BigDecimal("5000.00"));
        request.put("baseCurrency", game == CareerGame.ATS ? "USD" : "EUR");
        request.put("displayCurrency", game == CareerGame.ATS ? "USD" : "EUR");
        request.put("exchangeRate", new BigDecimal("1.00000000")); request.put("exchangeRateAsOf", "2026-08-26");
        request.put("baseCity", game == CareerGame.ATS ? "Phoenix, AZ" : "Berlin, Germany");
        request.put("defaultTruckMake", game == CareerGame.ATS ? "Kenworth" : "MAN");
        request.put("defaultTruckModel", game == CareerGame.ATS ? "T680" : "TGX");
        request.put("cityMarketVersion", "test-v1"); request.put("cityMarketLabel", "Test market");
        request.put("cityCostFactor", new BigDecimal("1.0000")); request.put("citySalaryFactor", new BigDecimal("1.0000"));
        if (game == CareerGame.ATS) request.put("stateCode", "AZ"); else request.put("countryCode", "DE");
        return Objects.requireNonNull(restTestClient.post().uri(CAREERS_PATH).headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(request).exchange().expectStatus().isCreated()
                .expectBody(CareerResponse.class).returnResult().getResponseBody());
    }

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(UUID.randomUUID(), email, email.toLowerCase(),
                "encoded-password-not-used-by-this-test", "Payslip Owner", UserStatus.ACTIVE, UserRole.USER,
                true, now, now, now, null));
    }
    private String accessToken(UserEntity user) { return accessTokenIssuer.issue(user, UUID.randomUUID()).token(); }
}
