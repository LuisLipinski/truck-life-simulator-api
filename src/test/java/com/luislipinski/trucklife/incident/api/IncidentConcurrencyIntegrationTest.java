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
import com.luislipinski.trucklife.incident.domain.IncidentStatus;
import com.luislipinski.trucklife.incident.persistence.IncidentPayslipDeductionRepository;
import com.luislipinski.trucklife.incident.persistence.IncidentRepository;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class IncidentConcurrencyIntegrationTest {

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
    void serializesConcurrentIncidentCancellationAndAtsPayslipClosing() throws Exception {
        UserEntity owner = saveUser("incident-race@example.com");
        String token = accessToken(owner);
        CareerResponse career = createCareer(token);
        IncidentResponse incident = createPendingIncident(token, career.id());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        int cancelStatus;
        int payslipStatus;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> cancel = executor.submit(
                    () -> concurrentCancelStatus(token, career.id(), incident.id(), ready, start)
            );
            Future<Integer> payslip = executor.submit(
                    () -> concurrentPayslipStatus(token, career.id(), ready, start)
            );

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            cancelStatus = cancel.get(20, TimeUnit.SECONDS);
            payslipStatus = payslip.get(20, TimeUnit.SECONDS);
        }

        assertThat(payslipStatus).isEqualTo(201);
        assertThat(cancelStatus).isIn(204, 409);
        assertThat(payslipRepository.count()).isEqualTo(1);
        assertThat(payrollPeriodRepository.count()).isEqualTo(1);
        assertThat(careerRepository.findById(career.id()).orElseThrow().getCurrentOperationalWeek())
                .isEqualTo(2);

        var persistedIncident = incidentRepository.findById(incident.id()).orElseThrow();
        var persistedPayslip = payslipRepository.findAllByCareerIdOrderByGeneratedAtDescIdDesc(career.id())
                .getFirst();

        if (cancelStatus == 204) {
            assertThat(persistedIncident.getStatus()).isEqualTo(IncidentStatus.CANCELLED);
            assertThat(persistedPayslip.getIncidentDeductionAmount()).isEqualByComparingTo("0.00");
            assertThat(incidentDeductionRepository.count()).isZero();
        } else {
            assertThat(persistedIncident.getStatus()).isEqualTo(IncidentStatus.DEDUCTED_PAYSLIP);
            assertThat(persistedPayslip.getIncidentDeductionAmount()).isEqualByComparingTo("25.00");
            assertThat(incidentDeductionRepository.count()).isEqualTo(1);
        }
    }

    private int concurrentCancelStatus(
            String token,
            UUID careerId,
            UUID incidentId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return restTestClient.delete()
                .uri(CAREERS_PATH + "/" + careerId + "/incidents/" + incidentId + "?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    private int concurrentPayslipStatus(
            String token,
            UUID careerId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return restTestClient.post()
                .uri(CAREERS_PATH + "/" + careerId + "/payslips?game=ATS")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedOperationalWeek", 1))
                .exchange()
                .expectBody()
                .returnResult()
                .getStatus()
                .value();
    }

    private IncidentResponse createPendingIncident(String token, UUID careerId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("expectedOperationalWeek", 1);
        request.put("type", "INFRACTION");
        request.put("amount", new BigDecimal("25.00"));
        request.put("route", "Phoenix");
        request.put("description", "Concurrent incident");
        request.put("chargeMethod", "PAYSLIP");

        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(CAREERS_PATH + "/" + careerId + "/incidents?game=ATS")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isCreated()
                        .expectBody(IncidentResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private CareerResponse createCareer(String token) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", CareerGame.ATS);
        request.put("driverName", "Concurrent Incident Driver");
        request.put("companyName", "Road Logistics");
        request.put("biography", "Incident concurrency test");
        request.put("initialBalance", new BigDecimal("5000.00"));
        request.put("baseCurrency", "USD");
        request.put("displayCurrency", "USD");
        request.put("exchangeRate", new BigDecimal("1.00000000"));
        request.put("exchangeRateAsOf", "2026-08-26");
        request.put("baseCity", "Phoenix, AZ");
        request.put("defaultTruckMake", "Kenworth");
        request.put("defaultTruckModel", "T680");
        request.put("cityMarketVersion", "client-test-v1");
        request.put("cityMarketLabel", "Client test market");
        request.put("cityCostFactor", new BigDecimal("9.0000"));
        request.put("citySalaryFactor", new BigDecimal("9.0000"));
        request.put("stateCode", "AZ");

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

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                "encoded-password-not-used-by-this-test",
                "Incident Race Owner",
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
