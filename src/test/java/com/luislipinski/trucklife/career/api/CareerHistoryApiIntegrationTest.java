package com.luislipinski.trucklife.career.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEventRepository;
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
class CareerHistoryApiIntegrationTest {

    private static final String CAREERS_PATH = "/api/v1/careers";

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
    private CareerEventRepository eventRepository;

    @Autowired
    private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        eventRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void recordsProfileEmployerAndBaseChangesAsAnImmutableCalendarlessTimeline() {
        UserEntity owner = saveUser("history-owner@example.com");
        String token = accessToken(owner);
        CareerResponse created = create(token, CareerGame.ATS, "Original Driver");

        CareerResponse profile = patch(
                token,
                created.id(),
                CareerGame.ATS,
                "",
                Map.of(
                        "version", created.version(),
                        "driverName", "Corrected Driver",
                        "biography", "Updated biography"
                )
        );
        CareerResponse employer = patch(
                token,
                created.id(),
                CareerGame.ATS,
                "/employer",
                Map.of(
                        "version", profile.version(),
                        "companyName", "New Logistics",
                        "effectiveDay", "tuesday"
                )
        );

        Map<String, Object> baseRequest = new LinkedHashMap<>();
        baseRequest.put("version", employer.version());
        baseRequest.put("effectiveDay", "wednesday");
        baseRequest.put("stateCode", "TX");
        baseRequest.put("baseCity", "Dallas, TX");
        baseRequest.put("baseCurrency", "USD");
        baseRequest.put("exchangeRate", new BigDecimal("5.30000000"));
        baseRequest.put("exchangeRateAsOf", "2026-08-26");
        baseRequest.put("cityMarketVersion", "test-v2");
        baseRequest.put("cityMarketLabel", "Dallas market");
        baseRequest.put("cityCostFactor", new BigDecimal("1.2000"));
        baseRequest.put("citySalaryFactor", new BigDecimal("1.1000"));
        CareerResponse base = patch(token, created.id(), CareerGame.ATS, "/base", baseRequest);

        assertThat(base.companyName()).isEqualTo("New Logistics");
        assertThat(base.baseCity()).isEqualTo("Dallas, TX");
        assertThat(base.stateCode()).isEqualTo("TX");
        assertThat(base.countryCode()).isNull();
        assertThat(base.displayCurrency()).isEqualTo("BRL");
        assertThat(base.version()).isEqualTo(3);

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + created.id() + "/events?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].type").isEqualTo("PROFILE_UPDATED")
                .jsonPath("$[0].operationalWeek").isEqualTo(1)
                .jsonPath("$[0].changes.driverName.previous").isEqualTo("Original Driver")
                .jsonPath("$[0].changes.driverName.next").isEqualTo("Corrected Driver")
                .jsonPath("$[0].changes.bio.previous").isEqualTo("Career API integration test")
                .jsonPath("$[0].changes.bio.next").isEqualTo("Updated biography")
                .jsonPath("$[1].type").isEqualTo("EMPLOYER_CHANGED")
                .jsonPath("$[1].operationalWeek").isEqualTo(1)
                .jsonPath("$[1].effectiveDay").isEqualTo("tuesday")
                .jsonPath("$[1].changes.company.previous").isEqualTo("Road Logistics")
                .jsonPath("$[1].changes.company.next").isEqualTo("New Logistics")
                .jsonPath("$[2].type").isEqualTo("BASE_CHANGED")
                .jsonPath("$[2].operationalWeek").isEqualTo(1)
                .jsonPath("$[2].effectiveDay").isEqualTo("wednesday")
                .jsonPath("$[2].changes.base.previous.city").isEqualTo("Phoenix, AZ")
                .jsonPath("$[2].changes.base.previous.stateCode").isEqualTo("AZ")
                .jsonPath("$[2].changes.base.next.city").isEqualTo("Dallas, TX")
                .jsonPath("$[2].changes.base.next.stateCode").isEqualTo("TX");

        assertThat(eventRepository.count()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidEffectiveWeekdayWithoutAppendingAnEvent() {
        UserEntity owner = saveUser("invalid-weekday@example.com");
        String token = accessToken(owner);
        CareerResponse created = create(token, CareerGame.ATS, "Weekday Driver");

        restTestClient.patch()
                .uri(CAREERS_PATH + "/" + created.id() + "/employer?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version", created.version(),
                        "companyName", "Invalid Day Logistics",
                        "effectiveDay", "funday"
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_EFFECTIVE_DAY_INVALID");

        assertThat(eventRepository.count()).isZero();
        assertThat(careerRepository.findById(created.id()).orElseThrow().getCompanyName())
                .isEqualTo("Road Logistics");
    }

    @Test
    void rejectsStaleHistoryMutationWithoutAppendingAnEvent() {
        UserEntity owner = saveUser("stale-history@example.com");
        String token = accessToken(owner);
        CareerResponse created = create(token, CareerGame.ATS, "Concurrent Driver");

        patch(
                token,
                created.id(),
                CareerGame.ATS,
                "/employer",
                Map.of(
                        "version", created.version(),
                        "companyName", "First Company",
                        "effectiveDay", "tuesday"
                )
        );

        restTestClient.patch()
                .uri(CAREERS_PATH + "/" + created.id() + "/employer?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version", created.version(),
                        "companyName", "Stale Company",
                        "effectiveDay", "wednesday"
                ))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_VERSION_CONFLICT");

        assertThat(eventRepository.count()).isEqualTo(1);
        restTestClient.get()
                .uri(CAREERS_PATH + "/" + created.id() + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.companyName").isEqualTo("First Company");
    }

    @Test
    void hidesHistoryAndHistoryMutationsFromOtherOwnersAndGames() {
        UserEntity owner = saveUser("private-history@example.com");
        UserEntity intruder = saveUser("history-intruder@example.com");
        String ownerToken = accessToken(owner);
        String intruderToken = accessToken(intruder);
        CareerResponse career = create(ownerToken, CareerGame.ATS, "Private Driver");

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/events?game=ATS")
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.patch()
                .uri(CAREERS_PATH + "/" + career.id() + "/employer?game=ATS")
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version", career.version(),
                        "companyName", "Intruder Logistics",
                        "effectiveDay", "wednesday"
                ))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.get()
                .uri(CAREERS_PATH + "/" + career.id() + "/events?game=ETS2")
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void validatesTheGameSpecificBaseLocationAndDocumentsCalendarlessHistoryContract() {
        UserEntity owner = saveUser("base-validation@example.com");
        String token = accessToken(owner);
        CareerResponse career = create(token, CareerGame.ATS, "Base Driver");

        restTestClient.patch()
                .uri(CAREERS_PATH + "/" + career.id() + "/base?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version", career.version(),
                        "effectiveDay", "wednesday",
                        "baseCity", "Dallas, TX",
                        "baseCurrency", "USD",
                        "exchangeRate", new BigDecimal("5.30000000")
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_LOCATION_INVALID");

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/events'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/employer'].patch.responses['409']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/base'].patch.responses['409']").exists()
                .jsonPath("$.components.schemas.ChangeCareerEmployerRequest.properties.effectiveDay").exists()
                .jsonPath("$.components.schemas.ChangeCareerBaseRequest.properties.effectiveDay").exists()
                .jsonPath("$.components.schemas.CareerEventResponse.properties.operationalWeek").exists()
                .jsonPath("$.components.schemas.CareerEventResponse.properties.effectiveDay").exists();

        String openApi = Objects.requireNonNull(restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());
        assertThat(openApi).doesNotContain("\"effectiveDate\"");
    }

    private CareerResponse create(String token, CareerGame game, String driverName) {
        return Objects.requireNonNull(restTestClient.post()
                .uri(CAREERS_PATH)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(createRequest(game, driverName))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CareerResponse.class)
                .returnResult()
                .getResponseBody());
    }

    private CareerResponse patch(
            String token,
            UUID careerId,
            CareerGame game,
            String suffix,
            Map<String, Object> request
    ) {
        return Objects.requireNonNull(restTestClient.patch()
                .uri(CAREERS_PATH + "/" + careerId + suffix + "?game=" + game)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(CareerResponse.class)
                .returnResult()
                .getResponseBody());
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
