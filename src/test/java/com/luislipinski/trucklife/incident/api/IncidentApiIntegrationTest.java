package com.luislipinski.trucklife.incident.api;

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
import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.domain.IncidentStatus;
import com.luislipinski.trucklife.incident.domain.IncidentType;
import com.luislipinski.trucklife.incident.persistence.IncidentPayslipDeductionRepository;
import com.luislipinski.trucklife.incident.persistence.IncidentRepository;
import com.luislipinski.trucklife.payroll.api.PayslipResponse;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
import com.luislipinski.trucklife.trip.api.TripResponse;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
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
class IncidentApiIntegrationTest {

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
    @Autowired private PayslipRepository payslipRepository;
    @Autowired private PayslipLineRepository payslipLineRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentPayslipDeductionRepository incidentDeductionRepository;
    @Autowired private JwtAccessTokenIssuer accessTokenIssuer;

    @BeforeEach
    void cleanPersistence() {
        incidentDeductionRepository.deleteAllInBatch();
        payslipLineRepository.deleteAllInBatch();
        incidentRepository.deleteAllInBatch();
        payslipRepository.deleteAllInBatch();
        payrollPeriodRepository.deleteAllInBatch();
        tripRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void registersImmediateAndDeferredIncidentsWithoutGameplayCalendarDates() {
        UserEntity owner = saveUser("incident-owner@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(
                token,
                CareerGame.ATS,
                "Incident Driver"
        );

        IncidentResponse immediate = createIncident(
                token,
                career.id(),
                CareerGame.ATS,
                1,
                IncidentType.INFRACTION,
                "75.00",
                null,
                "I-10 Phoenix area",
                "Speeding fine",
                IncidentChargeMethod.BALANCE
        );

        assertThat(immediate.operationalWeek()).isEqualTo(1);
        assertThat(immediate.status()).isEqualTo(IncidentStatus.PAID_BALANCE);
        assertThat(immediate.remainingAmount()).isEqualByComparingTo("0.00");
        assertThat(immediate.recordedAt()).isNotNull();
        assertThat(careerRepository.findById(career.id()).orElseThrow().getBalance())
                .isEqualByComparingTo("4925.00");

        IncidentResponse pending = createIncident(
                token,
                career.id(),
                CareerGame.ATS,
                1,
                IncidentType.ACCIDENT,
                "120.00",
                null,
                "US-60",
                "Minor cargo damage",
                IncidentChargeMethod.PAYSLIP
        );

        assertThat(pending.status()).isEqualTo(IncidentStatus.PENDING_PAYSLIP);
        assertThat(pending.remainingAmount()).isEqualByComparingTo("120.00");

        restTestClient.get()
                .uri(incidentsPath(career.id(), CareerGame.ATS))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);

        restTestClient.delete()
                .uri(incidentPath(career.id(), CareerGame.ATS, pending.id()))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isNoContent();

        IncidentResponse cancelled = getIncident(
                token,
                career.id(),
                CareerGame.ATS,
                pending.id()
        );
        assertThat(cancelled.status()).isEqualTo(IncidentStatus.CANCELLED);
        assertThat(cancelled.remainingAmount()).isEqualByComparingTo("0.00");

        restTestClient.delete()
                .uri(incidentPath(career.id(), CareerGame.ATS, immediate.id()))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INCIDENT_NOT_CANCELLABLE");
    }

    @Test
    void deductsAtsPendingIncidentOnPayslipAndTracksTheAllocation() {
        UserEntity owner = saveUser("ats-incident-payroll@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(
                token,
                CareerGame.ATS,
                "ATS Deduction Driver"
        );

        IncidentResponse incident = createIncident(
                token,
                career.id(),
                CareerGame.ATS,
                1,
                IncidentType.OTHER,
                "100.00",
                null,
                "Phoenix depot",
                "Employer equipment charge",
                IncidentChargeMethod.PAYSLIP
        );

        PayslipResponse payslip = generatePayslip(
                token,
                career.id(),
                CareerGame.ATS,
                Map.of("expectedOperationalWeek", 1)
        );

        assertThat(payslip.incidentDeductionAmount())
                .isEqualByComparingTo("100.00");
        assertThat(payslip.depositAmount()).isPositive();
        assertThat(payslip.contextSnapshot())
                .containsEntry("incidentDeductionsIncluded", true);
        assertThat((List<?>) payslip.contextSnapshot().get("sourceIncidentIds"))
                .contains(incident.id().toString());
        assertThat(payslip.lines())
                .extracting(PayslipResponse.LineResponse::code)
                .contains("INCIDENT_DEDUCTION");

        IncidentResponse paid = getIncident(
                token,
                career.id(),
                CareerGame.ATS,
                incident.id()
        );
        assertThat(paid.status()).isEqualTo(IncidentStatus.DEDUCTED_PAYSLIP);
        assertThat(paid.remainingAmount()).isEqualByComparingTo("0.00");
        assertThat(paid.deductions()).singleElement().satisfies(deduction -> {
            assertThat(deduction.payslipId()).isEqualTo(payslip.id());
            assertThat(deduction.amount()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void carriesIncidentRemainderWhenThePayslipCannotCoverTheWholeCharge() {
        UserEntity owner = saveUser("partial-incident@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(
                token,
                CareerGame.ATS,
                "Partial Deduction Driver"
        );

        IncidentResponse incident = createIncident(
                token,
                career.id(),
                CareerGame.ATS,
                1,
                IncidentType.ACCIDENT,
                "10000.00",
                null,
                "Phoenix",
                "Large accident charge",
                IncidentChargeMethod.PAYSLIP
        );

        PayslipResponse payslip = generatePayslip(
                token,
                career.id(),
                CareerGame.ATS,
                Map.of("expectedOperationalWeek", 1)
        );

        assertThat(payslip.depositAmount()).isEqualByComparingTo("0.00");
        assertThat(payslip.incidentDeductionAmount()).isPositive();

        IncidentResponse partial = getIncident(
                token,
                career.id(),
                CareerGame.ATS,
                incident.id()
        );
        assertThat(partial.status()).isEqualTo(IncidentStatus.PARTIALLY_DEDUCTED);
        assertThat(partial.remainingAmount()).isPositive();
        assertThat(partial.remainingAmount())
                .isEqualByComparingTo(
                        new BigDecimal("10000.00").subtract(
                                payslip.incidentDeductionAmount()
                        )
                );

        restTestClient.delete()
                .uri(incidentPath(career.id(), CareerGame.ATS, incident.id()))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INCIDENT_NOT_CANCELLABLE");
    }

    @Test
    void ets2PayslipChargesOnlyIncidentsEligibleThroughItsClosedWeeks() {
        UserEntity owner = saveUser("ets2-incident@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(
                token,
                CareerGame.ETS2,
                "ETS2 Incident Driver"
        );
        TripResponse weekOneTrip = createTrip(
                token,
                career.id(),
                CareerGame.ETS2
        );

        for (int week = 1; week <= 4; week++) {
            closeWeek(token, career.id(), week);
        }

        IncidentResponse currentOpenWeek = createIncident(
                token,
                career.id(),
                CareerGame.ETS2,
                5,
                IncidentType.INFRACTION,
                "100.00",
                null,
                "Berlin ring",
                "Current open week fine",
                IncidentChargeMethod.PAYSLIP
        );
        IncidentResponse oldTripIncident = createIncident(
                token,
                career.id(),
                CareerGame.ETS2,
                5,
                IncidentType.TOLL_CHARGE,
                "50.00",
                weekOneTrip.id(),
                null,
                "Week one road charge",
                IncidentChargeMethod.PAYSLIP
        );

        assertThat(currentOpenWeek.operationalWeek()).isEqualTo(5);
        assertThat(oldTripIncident.operationalWeek()).isEqualTo(1);
        assertThat(oldTripIncident.route()).contains("Berlin", "Hamburg");

        PayslipResponse payslip = generatePayslip(
                token,
                career.id(),
                CareerGame.ETS2,
                Map.of("expectedPayrollMonth", 1)
        );

        assertThat(payslip.incidentDeductionAmount())
                .isEqualByComparingTo("50.00");
        assertThat((List<?>) payslip.contextSnapshot().get("sourceIncidentIds"))
                .containsExactly(oldTripIncident.id().toString());

        assertThat(getIncident(
                token,
                career.id(),
                CareerGame.ETS2,
                oldTripIncident.id()
        ).status()).isEqualTo(IncidentStatus.DEDUCTED_PAYSLIP);

        IncidentResponse stillPending = getIncident(
                token,
                career.id(),
                CareerGame.ETS2,
                currentOpenWeek.id()
        );
        assertThat(stillPending.status()).isEqualTo(IncidentStatus.PENDING_PAYSLIP);
        assertThat(stillPending.remainingAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void enforcesOwnershipWeekValidationAuthenticationAndOpenApi() {
        UserEntity owner = saveUser("incident-private@example.com");
        UserEntity intruder = saveUser("incident-intruder@example.com");
        String ownerToken = accessToken(owner);
        String intruderToken = accessToken(intruder);
        CareerResponse career = createCareer(
                ownerToken,
                CareerGame.ATS,
                "Private Incident Driver"
        );

        restTestClient.get()
                .uri(incidentsPath(career.id(), CareerGame.ATS))
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.get()
                .uri(incidentsPath(career.id(), CareerGame.ATS))
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.post()
                .uri(incidentsPath(career.id(), CareerGame.ATS))
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "expectedOperationalWeek", 2,
                        "type", "INFRACTION",
                        "amount", new BigDecimal("20.00"),
                        "route", "Phoenix",
                        "description", "Stale week",
                        "chargeMethod", "PAYSLIP"
                ))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INCIDENT_WEEK_CONFLICT");

        restTestClient.post()
                .uri(incidentsPath(career.id(), CareerGame.ATS))
                .headers(headers -> headers.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "expectedOperationalWeek", 1,
                        "type", "INFRACTION",
                        "amount", BigDecimal.ZERO,
                        "description", "Invalid amount",
                        "chargeMethod", "PAYSLIP"
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/incidents'].post.responses['201']")
                .exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/incidents'].get.responses['200']")
                .exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/incidents/{incidentId}'].delete.responses['204']")
                .exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/incidents'].post.security[0].bearerAuth")
                .exists();
    }

    private IncidentResponse createIncident(
            String token,
            UUID careerId,
            CareerGame game,
            int expectedWeek,
            IncidentType type,
            String amount,
            UUID relatedTripId,
            String route,
            String description,
            IncidentChargeMethod chargeMethod
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("expectedOperationalWeek", expectedWeek);
        request.put("type", type);
        request.put("amount", new BigDecimal(amount));
        if (relatedTripId != null) request.put("relatedTripId", relatedTripId);
        if (route != null) request.put("route", route);
        request.put("description", description);
        request.put("chargeMethod", chargeMethod);

        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(incidentsPath(careerId, game))
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(IncidentResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private IncidentResponse getIncident(
            String token,
            UUID careerId,
            CareerGame game,
            UUID incidentId
    ) {
        return Objects.requireNonNull(
                restTestClient.get()
                        .uri(incidentPath(careerId, game, incidentId))
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(IncidentResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private PayslipResponse generatePayslip(
            String token,
            UUID careerId,
            CareerGame game,
            Map<String, Object> body
    ) {
        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(CAREERS_PATH + "/" + careerId + "/payslips?game=" + game)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(PayslipResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private void closeWeek(
            String token,
            UUID careerId,
            int expectedWeek
    ) {
        restTestClient.post()
                .uri(
                        CAREERS_PATH + "/" + careerId
                                + "/payroll-periods/close?game=ETS2"
                )
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", expectedWeek))
                .exchange()
                .expectStatus().isCreated();
    }

    private TripResponse createTrip(
            String token,
            UUID careerId,
            CareerGame game
    ) {
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
        request.put("breakMinutes", 0);
        request.put("truckMake", "MAN");
        request.put("truckModel", "TGX");

        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(CAREERS_PATH + "/" + careerId + "/trips?game=" + game)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(TripResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private CareerResponse createCareer(
            String token,
            CareerGame game,
            String driverName
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game);
        request.put("driverName", driverName);
        request.put("companyName", "Road Logistics");
        request.put("biography", "Incident API integration test");
        request.put("initialBalance", new BigDecimal("5000.00"));
        request.put("baseCurrency", game == CareerGame.ATS ? "USD" : "EUR");
        request.put("displayCurrency", game == CareerGame.ATS ? "USD" : "EUR");
        request.put("exchangeRate", new BigDecimal("1.00000000"));
        request.put("exchangeRateAsOf", "2026-08-26");
        request.put(
                "baseCity",
                game == CareerGame.ATS ? "Phoenix, AZ" : "Berlin, Germany"
        );
        request.put(
                "defaultTruckMake",
                game == CareerGame.ATS ? "Kenworth" : "MAN"
        );
        request.put(
                "defaultTruckModel",
                game == CareerGame.ATS ? "T680" : "TGX"
        );
        request.put("cityMarketVersion", "client-test-v1");
        request.put("cityMarketLabel", "Client test market");
        request.put("cityCostFactor", new BigDecimal("9.0000"));
        request.put("citySalaryFactor", new BigDecimal("9.0000"));
        if (game == CareerGame.ATS) {
            request.put("stateCode", "AZ");
        } else {
            request.put("countryCode", "DE");
        }

        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(CAREERS_PATH)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(CareerResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private String incidentsPath(UUID careerId, CareerGame game) {
        return CAREERS_PATH + "/" + careerId + "/incidents?game=" + game;
    }

    private String incidentPath(
            UUID careerId,
            CareerGame game,
            UUID incidentId
    ) {
        return CAREERS_PATH + "/" + careerId
                + "/incidents/" + incidentId + "?game=" + game;
    }

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "encoded-password-not-used-by-this-test",
                "Incident Owner",
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
        return accessTokenIssuer.issue(
                user,
                UUID.randomUUID()
        ).token();
    }
}
