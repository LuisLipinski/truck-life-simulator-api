package com.luislipinski.trucklife.trip.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
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
class TripDeleteApiIntegrationTest {

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
    void deletesOnlyTripsFromTheCurrentOpenOperationalWeekAndDocumentsTheContract() {
        UserEntity owner = saveUser("trip-delete-owner@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token, CareerGame.ATS, "Delete Driver");
        TripResponse currentTrip = createTrip(token, career.id(), CareerGame.ATS);

        restTestClient.delete()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips/" + currentTrip.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");

        assertThat(tripRepository.count()).isZero();

        TripResponse lockedTrip = createTrip(token, career.id(), CareerGame.ATS);
        CareerEntity persistedCareer = careerRepository.findById(career.id()).orElseThrow();
        persistedCareer.advanceOperationalWeek(Instant.now());
        careerRepository.saveAndFlush(persistedCareer);

        restTestClient.delete()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips/" + lockedTrip.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("TRIP_WEEK_LOCKED");

        assertThat(tripRepository.findById(lockedTrip.id())).isPresent();

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/trips/{tripId}'].delete.responses['204']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/trips/{tripId}'].delete.responses['409']").exists();
    }

    @Test
    void hidesTripDeletionBehindCareerOwnershipAndGameBoundaries() {
        UserEntity owner = saveUser("trip-delete-private@example.com");
        UserEntity intruder = saveUser("trip-delete-intruder@example.com");
        String ownerToken = accessToken(owner);
        String intruderToken = accessToken(intruder);
        CareerResponse career = createCareer(ownerToken, CareerGame.ATS, "Private Delete Driver");
        TripResponse trip = createTrip(ownerToken, career.id(), CareerGame.ATS);

        restTestClient.delete()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips/" + trip.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.delete()
                .uri(CAREERS_PATH + "/" + career.id() + "/trips/" + trip.id() + "?game=ETS2")
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        assertThat(tripRepository.findById(trip.id())).isPresent();
    }

    private TripResponse createTrip(String token, UUID careerId, CareerGame game) {
        return Objects.requireNonNull(restTestClient.post()
                .uri(CAREERS_PATH + "/" + careerId + "/trips?game=" + game)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(loadedTrip())
                .exchange()
                .expectStatus().isCreated()
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
        request.put("biography", "Trip delete integration test");
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
                "Trip Delete Owner",
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
