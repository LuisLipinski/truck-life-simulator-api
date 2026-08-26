package com.luislipinski.trucklife.trip.api;

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
import com.luislipinski.trucklife.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
class TripApiIntegrationTest {

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
    @Autowired private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        tripRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createsListsAndGetsAManualTripUsingWeekdaySemanticsAndHistoricalSnapshots() {
        UserEntity owner = saveUser("trip-owner@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "Trip Driver");

        TripResponse trip = createTrip(token, career.id(), CareerGame.ATS, loadedTrip());

        assertThat(trip.game()).isEqualTo(CareerGame.ATS);
        assertThat(trip.operationalWeek()).isEqualTo(1);
        assertThat(trip.departureDay()).isEqualTo("monday");
        assertThat(trip.arrivalDay()).isEqualTo("monday");
        assertThat(trip.type().name()).isEqualTo("LOADED");
        assertThat(trip.paymentCategory().name()).isEqualTo("NORMAL");
        assertThat(trip.officialDistance()).isEqualByComparingTo("120.00");
        assertThat(trip.odometerDistance()).isEqualByComparingTo("124.5");
        assertThat(trip.source().name()).isEqualTo("MANUAL");
        assertThat(trip.employerSnapshot()).containsEntry("companyName", "Road Logistics");
        assertThat(trip.baseSnapshot()).containsEntry("city", "Phoenix, AZ");
        assertThat(trip.baseSnapshot()).containsEntry("currency", "BRL");

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(trip.id().toString())
                .jsonPath("$[0].officialDistance").isEqualTo(120.0)
                .jsonPath("$[0].odometerDistance").isEqualTo(124.5);

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips/" + trip.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(trip.id().toString())
                .jsonPath("$.source").isEqualTo("MANUAL");
    }

    @Test
    void canonicalizesDeadheadAndKeepsOfficialDistanceIndependentFromOdometer() {
        UserEntity owner = saveUser("deadhead-owner@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "Deadhead Driver");
        Map<String, Object> request = loadedTrip();
        request.put("type", "Deadhead");
        request.put("paymentCategory", "hazmat");
        request.put("cargo", "Should not survive");
        request.put("officialDistance", new BigDecimal("100.00"));
        request.put("odometerStart", new BigDecimal("1000.0"));
        request.put("odometerEnd", new BigDecimal("1117.0"));

        TripResponse trip = createTrip(token, career.id(), CareerGame.ATS, request);

        assertThat(trip.type().name()).isEqualTo("DEADHEAD");
        assertThat(trip.paymentCategory().name()).isEqualTo("DEADHEAD");
        assertThat(trip.cargo()).isNull();
        assertThat(trip.officialDistance()).isEqualByComparingTo("100.00");
        assertThat(trip.odometerDistance()).isEqualByComparingTo("117.0");
    }

    @Test
    void rejectsInvalidScheduleOdometerBreakAndLevelOneCategory() {
        UserEntity owner = saveUser("trip-validation@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "Validation Driver");

        Map<String, Object> invalidSchedule = loadedTrip();
        invalidSchedule.put("departureTime", "18:00");
        invalidSchedule.put("arrivalTime", "17:00");
        expectTripProblem(token, career.id(), invalidSchedule, "TRIP_SCHEDULE_INVALID");

        Map<String, Object> invalidOdometer = loadedTrip();
        invalidOdometer.remove("odometerEnd");
        expectTripProblem(token, career.id(), invalidOdometer, "TRIP_ODOMETER_INVALID");

        Map<String, Object> invalidBreak = loadedTrip();
        invalidBreak.put("breakMinutes", 300);
        expectTripProblem(token, career.id(), invalidBreak, "TRIP_BREAK_INVALID");

        Map<String, Object> invalidCategory = loadedTrip();
        invalidCategory.put("paymentCategory", "hazmat");
        expectTripProblem(token, career.id(), invalidCategory, "TRIP_PAYMENT_CATEGORY_INVALID");

        assertThat(tripRepository.count()).isZero();
    }

    @Test
    void acceptsWeekWrapAndEts2BreakWithoutCalendarDates() {
        UserEntity owner = saveUser("ets-trip@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ETS2, "European Driver");
        Map<String, Object> request = loadedTrip();
        request.put("departureDay", "sunday");
        request.put("departureTime", "22:00");
        request.put("arrivalDay", "monday");
        request.put("arrivalTime", "02:00");
        request.put("breakMinutes", 45);

        TripResponse trip = createTrip(token, career.id(), CareerGame.ETS2, request);

        assertThat(trip.departureDay()).isEqualTo("sunday");
        assertThat(trip.arrivalDay()).isEqualTo("monday");
        assertThat(trip.breakMinutes()).isEqualTo(45);
        assertThat(trip.baseSnapshot()).containsEntry("city", "Berlin, Germany");
    }

    @Test
    void hidesTripsBehindCareerOwnershipAndGameBoundaries() {
        UserEntity owner = saveUser("private-trip@example.com");
        UserEntity intruder = saveUser("trip-intruder@example.com");
        String ownerToken = accessToken(owner);
        String intruderToken = accessToken(intruder);
        CareerResponse career = createCareer(ownerToken, CareerGame.ATS, "Private Driver");
        TripResponse trip = createTrip(ownerToken, career.id(), CareerGame.ATS, loadedTrip());

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips?game=ATS")
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips/" + trip.id() + "?game=ETS2")
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.post()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips?game=ATS")
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(loadedTrip())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        assertThat(tripRepository.count()).isEqualTo(1);
    }

    @Test
    void filtersByOperationalWeekAndDocumentsTripEndpoints() {
        UserEntity owner = saveUser("trip-contract@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "Contract Driver");
        createTrip(token, career.id(), CareerGame.ATS, loadedTrip());

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips?game=ATS&operationalWeek=1")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips?game=ATS&operationalWeek=0")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TRIP_WEEK_INVALID");

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/trips'].post.responses['201']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/trips'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/trips/{tripId}'].get.responses['200']").exists();
    }

    @Test
    void requiresAuthenticationForTripRoutes() {
        UUID careerId = UUID.randomUUID();
        restTestClient.get()
                .uri(CAREERS_PATH + "/" + careerId + "/trips?game=ATS")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private void expectTripProblem(String token, UUID careerId, Map<String, Object> body, String code) {
        restTestClient.post()
                .uri(CAREERS_PATH + "/" + careerId + "/trips?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(code);
    }

    private TripResponse createTrip(String token, UUID careerId, CareerGame game, Map<String, Object> request) {
        return Objects.requireNonNull(restTestClient.post()
                .uri(CAREERS_PATH + "/" + careerId + "/trips?game=" + game)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(TripResponse.class)
                .returnResult()
                .getResponseBody());
    }

    private Map<String, Object> loadedTrip() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("departureDay", "monday");
        request.put("departureTime", "08:00");
        request.put("arrivalDay", "monday");
        request.put("arrivalTime", "12:00");
        request.put("originCity", "Phoenix, AZ");
        request.put("originCompany", "Road Logistics");
        request.put("destinationCity", "Tucson, AZ");
        request.put("destinationCompany", "Customer Depot");
        request.put("cargo", "Food");
        request.put("type", "Loaded");
        request.put("paymentCategory", "normal");
        request.put("officialDistance", new BigDecimal("120.00"));
        request.put("truckMake", "Kenworth");
        request.put("truckModel", "T680");
        request.put("odometerStart", new BigDecimal("1000.0"));
        request.put("odometerEnd", new BigDecimal("1124.5"));
        return request;
    }

    private CareerResponse createCareer(String token, CareerGame game, String driverName) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game);
        request.put("driverName", driverName);
        request.put("companyName", "Road Logistics");
        request.put("biography", "Trip API integration test");
        request.put("initialBalance", new BigDecimal("5000.00"));
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
        if (game == CareerGame.ATS) request.put("stateCode", "AZ");
        else request.put("countryCode", "DE");

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
                "Trip Owner",
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
