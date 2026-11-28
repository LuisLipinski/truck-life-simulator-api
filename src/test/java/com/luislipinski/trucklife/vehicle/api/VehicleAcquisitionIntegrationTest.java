package com.luislipinski.trucklife.vehicle.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEventRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialContractRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialInstallmentRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialPaymentRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.vehicle.application.VehicleOperations;
import com.luislipinski.trucklife.vehicle.domain.VehicleAcquisitionType;
import com.luislipinski.trucklife.vehicle.domain.VehicleCondition;
import com.luislipinski.trucklife.vehicle.persistence.CareerVehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
class VehicleAcquisitionIntegrationTest {
    private static final String CAREERS = "/api/v1/careers";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired RestTestClient restTestClient;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired CareerVehicleRepository vehicleRepository;
    @Autowired FinancialContractRepository contractRepository;
    @Autowired FinancialInstallmentRepository installmentRepository;
    @Autowired FinancialPaymentRepository paymentRepository;
    @Autowired FinancialContractEventRepository contractEventRepository;
    @Autowired LedgerEntryRepository ledgerRepository;
    @Autowired VehicleOperations vehicleOperations;
    @Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void clean() {
        ledgerRepository.deleteAllInBatch();
        contractEventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        installmentRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void cashPurchaseCreatesOwnedTruckDebitsBalanceAndUnlocksPhase2Idempotently() {
        UserEntity owner = saveUser("cash-truck@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ATS", "120000.00", "USD", "US", "AZ", "Phoenix, AZ");

        UUID operationId = UUID.randomUUID();
        Map<String, Object> request = acquisitionRequest(
                operationId,
                "CASH",
                "Peterbilt",
                "579",
                2025,
                "12500.000",
                "80000.00",
                null,
                1,
                null,
                "120000.00"
        );

        VehicleResponse created = acquire(token, career.id(), "ATS", request, 201);
        assertThat(created.acquisitionType()).isEqualTo(VehicleAcquisitionType.CASH);
        assertThat(created.financingContractId()).isNull();
        assertThat(created.phase2Unlocked()).isTrue();
        assertThat(created.currentPhase()).isEqualTo((short) 2);
        assertThat(created.currentBalance()).isEqualByComparingTo("40000.00");

        CareerEntity persistedCareer = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persistedCareer.getCurrentPhase()).isEqualTo((short) 2);
        assertThat(persistedCareer.getBalance()).isEqualByComparingTo("40000.00");
        assertThat(persistedCareer.getDefaultTruckMake()).isEqualTo("Peterbilt");
        assertThat(persistedCareer.getDefaultTruckModel()).isEqualTo("579");
        assertThat(vehicleRepository.count()).isEqualTo(1);
        assertThat(contractRepository.count()).isZero();

        VehicleResponse replay = acquire(token, career.id(), "ATS", request, 200);
        assertThat(replay.id()).isEqualTo(created.id());
        assertThat(vehicleRepository.count()).isEqualTo(1);
        assertThat(careerRepository.findById(career.id()).orElseThrow().getBalance()).isEqualByComparingTo("40000.00");
    }

    @Test
    void financedPurchaseCreatesVehicleAndLinksServerCalculatedFinancingBeforeUnlockingPhase2() {
        UserEntity owner = saveUser("financed-truck@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ATS", "30000.00", "USD", "US", "CA", "Los Angeles, CA");

        VehicleResponse created = acquire(
                token,
                career.id(),
                "ATS",
                acquisitionRequest(
                        UUID.randomUUID(),
                        "FINANCED",
                        "Kenworth",
                        "T680",
                        2026,
                        "0.000",
                        "100000.00",
                        78,
                        1,
                        null,
                        "30000.00"
                ),
                201
        );

        assertThat(created.financingContractId()).isNotNull();
        assertThat(created.currentBalance()).isEqualByComparingTo("10000.00");
        assertThat(created.currentPhase()).isEqualTo((short) 2);

        var contract = contractRepository.findById(created.financingContractId()).orElseThrow();
        assertThat(contract.getVehicleId()).isEqualTo(created.id());
        assertThat(contract.getRequestedAmount()).isEqualByComparingTo("100000.00");
        assertThat(contract.getDownPayment()).isEqualByComparingTo("20000.00");
        assertThat(contract.getPrincipal()).isEqualByComparingTo("80000.00");

        CareerResponse refreshed = getCareer(token, career.id(), "ATS");
        assertThat(refreshed.currentPhase()).isEqualTo((short) 2);
        assertThat(refreshed.balance()).isEqualByComparingTo("10000.00");
    }

    @Test
    void ets2AcquisitionKeepsCalendarlessOperationalMonthAndRejectsSecondActiveTruck() {
        UserEntity owner = saveUser("ets-truck@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ETS2", "90000.00", "EUR", "DE", null, "Berlin");

        VehicleResponse created = acquire(
                token,
                career.id(),
                "ETS2",
                acquisitionRequest(
                        UUID.randomUUID(),
                        "CASH",
                        "MAN",
                        "TGX",
                        2024,
                        "45000.000",
                        "70000.00",
                        null,
                        1,
                        1,
                        "90000.00"
                ),
                201
        );
        assertThat(created.acquiredOperationalWeek()).isEqualTo(1);
        assertThat(created.acquiredPayrollMonth()).isEqualTo(1);

        restTestClient.post()
                .uri(path(career.id(), "ETS2"))
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(acquisitionRequest(
                        UUID.randomUUID(),
                        "CASH",
                        "Scania",
                        "S",
                        2025,
                        "0.000",
                        "10000.00",
                        null,
                        1,
                        1,
                        "20000.00"
                ))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("FIRST_TRUCK_ALREADY_ACQUIRED");

        assertThat(vehicleRepository.count()).isEqualTo(1);
    }

    @Test
    void newAtsTruckUsesSelectedPurchaseStateWithoutInventingAStateSpecificBaseRate() {
        UserEntity owner = saveUser("ats-purchase-state@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ATS", "30000.00", "USD", "US", "AZ", "Phoenix, AZ");

        var offers = Objects.requireNonNull(
                restTestClient.get()
                        .uri(CAREERS + "/" + career.id() + "/vehicles/financing-offers?game=ATS&condition=NEW&purchasePrice=100000.00&purchaseStateCode=TX")
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(com.luislipinski.trucklife.finance.api.FinancingOfferResponse[].class)
                        .returnResult()
                        .getResponseBody()
        );
        assertThat(offers).hasSize(3);
        assertThat(offers[0].jurisdictionStateCode()).isEqualTo("TX");
        assertThat(offers[0].annualInterestRate()).isEqualByComparingTo("0.0714000000");

        Map<String, Object> request = acquisitionRequest(
                UUID.randomUUID(), "FINANCED", "Kenworth", "T680", 2026, "0.000", "100000.00", 78, 1, null, "30000.00"
        );
        request.put("vehicleCondition", "NEW");
        request.put("purchaseStateCode", "TX");

        VehicleResponse created = acquire(token, career.id(), "ATS", request, 201);
        assertThat(created.vehicleCondition()).isEqualTo(VehicleCondition.NEW);
        assertThat(created.purchaseCountryCode()).isEqualTo("US");
        assertThat(created.purchaseStateCode()).isEqualTo("TX");
        var contract = contractRepository.findById(created.financingContractId()).orElseThrow();
        assertThat(contract.getJurisdictionStateCode()).isEqualTo("TX");
        assertThat(contract.getAnnualInterestRate()).isEqualByComparingTo("0.0714000000");
    }

    @Test
    void newEts2TruckUsesSelectedPurchaseCountryWhileUsedTruckIsForcedToHeadquarters() {
        UserEntity owner = saveUser("ets-purchase-country@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ETS2", "30000.00", "EUR", "DE", null, "Berlin");

        var offers = Objects.requireNonNull(
                restTestClient.get()
                        .uri(CAREERS + "/" + career.id() + "/vehicles/financing-offers?game=ETS2&condition=NEW&purchasePrice=50000.00&purchaseCountryCode=PL")
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(com.luislipinski.trucklife.finance.api.FinancingOfferResponse[].class)
                        .returnResult()
                        .getResponseBody()
        );
        assertThat(offers).hasSize(3);
        assertThat(offers[0].jurisdictionCountryCode()).isEqualTo("PL");
        assertThat(offers[0].annualInterestRate()).isEqualByComparingTo("0.1027000000");

        restTestClient.get()
                .uri(CAREERS + "/" + career.id() + "/vehicles/financing-offers?game=ETS2&condition=USED&purchasePrice=50000.00&purchaseCountryCode=PL")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("USED_TRUCK_JURISDICTION_FIXED");

        var usedOffers = Objects.requireNonNull(
                restTestClient.get()
                        .uri(CAREERS + "/" + career.id() + "/vehicles/financing-offers?game=ETS2&condition=USED&purchasePrice=50000.00")
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(com.luislipinski.trucklife.finance.api.FinancingOfferResponse[].class)
                        .returnResult()
                        .getResponseBody()
        );
        assertThat(usedOffers[0].jurisdictionCountryCode()).isEqualTo("DE");
        assertThat(usedOffers[0].annualInterestRate()).isEqualByComparingTo("0.0812000000");
    }

    @Test
    void serializesConcurrentFirstTruckAcquisitionsWithoutDoubleDebit() throws Exception {
        UserEntity owner = saveUser("concurrent-truck@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ATS", "120000.00", "USD", "US", "AZ", "Phoenix, AZ");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentCashAcquire(owner, career, "Peterbilt", "579", ready, start));
            var second = executor.submit(() -> concurrentCashAcquire(owner, career, "Kenworth", "T680", ready, start));

            ready.await();
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("SUCCESS", "FIRST_TRUCK_ALREADY_ACQUIRED");
        }

        assertThat(vehicleRepository.count()).isEqualTo(1);
        CareerEntity persisted = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persisted.getBalance()).isEqualByComparingTo("40000.00");
        assertThat(persisted.getCurrentPhase()).isEqualTo((short) 2);
    }

    @Test
    void enforcesOwnershipAndDocumentsVehicleEndpoints() {
        UserEntity owner = saveUser("vehicle-owner@example.com");
        UserEntity intruder = saveUser("vehicle-intruder@example.com");
        String ownerToken = token(owner);
        String intruderToken = token(intruder);
        CareerResponse career = createCareer(ownerToken, "ATS", "90000.00", "USD", "US", "TX", "Dallas, TX");

        restTestClient.get()
                .uri("/api/v1/careers/" + career.id() + "/vehicles?game=ATS")
                .headers(headers -> headers.setBearerAuth(intruderToken))
                .exchange()
                .expectStatus().isNotFound();

        restTestClient.get()
                .uri("/api/v1/careers/" + career.id() + "/vehicles?game=ATS")
                .exchange()
                .expectStatus().isUnauthorized();

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/vehicles'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/vehicles/financing-offers'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/vehicles/acquisitions'].post.responses['201']").exists();
    }

    private String concurrentCashAcquire(
            UserEntity owner,
            CareerResponse career,
            String make,
            String model,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        try {
            vehicleOperations.acquire(
                    owner.getId(),
                    CareerGame.ATS,
                    career.id(),
                    new VehicleOperations.AcquireVehicleCommand(
                            UUID.randomUUID(),
                            VehicleAcquisitionType.CASH,
                            VehicleCondition.USED,
                            null,
                            null,
                            make,
                            model,
                            (short) 2026,
                            new BigDecimal("0.000"),
                            new BigDecimal("80000.00"),
                            null,
                            1,
                            null,
                            new BigDecimal("120000.00")
                    )
            );
            return "SUCCESS";
        } catch (ApiProblemException ex) {
            return ex.code();
        }
    }

    private VehicleResponse acquire(String token, UUID careerId, String game, Map<String, Object> body, int status) {
        var response = restTestClient.post()
                .uri(path(careerId, game))
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(VehicleResponse.class)
                .returnResult()
                .getResponseBody();
        return Objects.requireNonNull(response);
    }

    private CareerResponse getCareer(String token, UUID careerId, String game) {
        return Objects.requireNonNull(
                restTestClient.get()
                        .uri(CAREERS + "/" + careerId + "?game=" + game)
                        .headers(headers -> headers.setBearerAuth(token))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(CareerResponse.class)
                        .returnResult()
                        .getResponseBody()
        );
    }

    private Map<String, Object> acquisitionRequest(
            UUID operationId,
            String acquisitionType,
            String make,
            String model,
            int year,
            String odometer,
            String price,
            Integer financingTermPeriods,
            int week,
            Integer month,
            String balance
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("operationId", operationId);
        value.put("acquisitionType", acquisitionType);
        value.put("vehicleCondition", "USED");
        value.put("make", make);
        value.put("model", model);
        value.put("modelYear", year);
        value.put("initialOdometer", new BigDecimal(odometer));
        value.put("purchasePrice", new BigDecimal(price));
        if (financingTermPeriods != null) value.put("financingTermPeriods", financingTermPeriods);
        value.put("expectedOperationalWeek", week);
        if (month != null) value.put("expectedPayrollMonth", month);
        value.put("expectedBalance", new BigDecimal(balance));
        return value;
    }

    private String path(UUID careerId, String game) {
        return CAREERS + "/" + careerId + "/vehicles/acquisitions?game=" + game;
    }

    private CareerResponse createCareer(
            String token,
            String game,
            String balance,
            String currency,
            String country,
            String state,
            String city
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("game", game);
        request.put("driverName", "Truck Owner");
        request.put("companyName", "Road Logistics");
        request.put("initialBalance", new BigDecimal(balance));
        request.put("baseCurrency", currency);
        request.put("displayCurrency", currency);
        request.put("exchangeRate", new BigDecimal("1.00000000"));
        request.put("exchangeRateAsOf", "2026-11-27");
        if (state != null) request.put("stateCode", state);
        if (country != null) request.put("countryCode", country);
        request.put("baseCity", city);
        request.put("cityMarketVersion", "test-v1");
        request.put("cityMarketLabel", "Test market");
        request.put("cityCostFactor", new BigDecimal("1.0000"));
        request.put("citySalaryFactor", new BigDecimal("1.0000"));
        return Objects.requireNonNull(
                restTestClient.post()
                        .uri(CAREERS)
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

    private String token(UserEntity user) {
        return tokenIssuer.issue(user, UUID.randomUUID()).token();
    }
}
