package com.luislipinski.trucklife.phase2.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.api.CareerResponse;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
import com.luislipinski.trucklife.phase2.domain.Phase2AffiliationChoice;
import com.luislipinski.trucklife.phase2.persistence.CarrierContractRepository;
import com.luislipinski.trucklife.phase2.persistence.Phase2TransitionRepository;
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
class Phase2TransitionApiIntegrationTest {
    private static final String CAREERS = "/api/v1/careers";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired RestTestClient restTestClient;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired PayslipRepository payslipRepository;
    @Autowired Phase2TransitionRepository transitionRepository;
    @Autowired CarrierContractRepository carrierContractRepository;
    @Autowired LedgerEntryRepository ledgerRepository;
    @Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void clean() {
        carrierContractRepository.deleteAllInBatch();
        transitionRepository.deleteAllInBatch();
        ledgerRepository.deleteAllInBatch();
        payslipRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void blocksTransitionUntilPhase2IsUnlockedAndHidesOtherOwnersCareer() {
        UserEntity owner = saveUser("phase2-owner@example.com");
        UserEntity intruder = saveUser("phase2-intruder@example.com");
        String ownerToken = token(owner);
        CareerResponse career = createCareer(ownerToken, "ATS", "10000.00", "USD", "US", "AZ", "Phoenix, AZ");

        restTestClient.get()
                .uri(path(career.id(), "ATS"))
                .headers(h -> h.setBearerAuth(ownerToken))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PHASE2_NOT_UNLOCKED");

        unlock(career.id());

        restTestClient.get()
                .uri(path(career.id(), "ATS"))
                .headers(h -> h.setBearerAuth(token(intruder)))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_NOT_FOUND");

        restTestClient.get()
                .uri(path(career.id(), "ATS"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void atsOtherCarrierCompletionKeepsHomeBaseAndDoesNotInventSeverance() {
        UserEntity owner = saveUser("phase2-ats@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ATS", "10000.00", "USD", "US", "AZ", "Phoenix, AZ");
        unlock(career.id());

        Phase2TransitionResponse pending = get(token, career.id(), "ATS");
        assertThat(pending.status().name()).isEqualTo("PENDING");
        assertThat(pending.previousCompanyName()).isEqualTo("Road Logistics");
        assertThat(pending.terminationStateCode()).isEqualTo("AZ");
        assertThat(pending.statutorySettlementAmount()).isEqualByComparingTo("0.00");
        assertThat(pending.policySource()).contains("dol.gov");
        assertThat(pending.carrierContract()).isNull();

        UUID operationId = UUID.randomUUID();
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("operationId", operationId);
        request.put("affiliation", "OTHER_COMPANY");
        request.put("carrierName", "Texas Haul");
        request.put("carrierStateCode", "TX");
        request.put("effectiveDay", "MONDAY");
        request.put("expectedBalance", new BigDecimal("10000.00"));

        Phase2TransitionResponse completed = complete(token, career.id(), "ATS", request);
        assertThat(completed.status().name()).isEqualTo("COMPLETED");
        assertThat(completed.selectedAffiliation()).isEqualTo(Phase2AffiliationChoice.OTHER_COMPANY);
        assertThat(completed.currentBalance()).isEqualByComparingTo("10000.00");
        assertThat(completed.carrierContract()).isNotNull();
        assertThat(completed.carrierContract().carrierName()).isEqualTo("Texas Haul");
        assertThat(completed.carrierContract().jurisdictionCountryCode()).isEqualTo("US");
        assertThat(completed.carrierContract().jurisdictionStateCode()).isEqualTo("TX");
        assertThat(completed.carrierContract().compensationRuleKey()).isEqualTo("ATS:TX");
        assertThat(completed.carrierContract().compensationPolicyStatus()).isEqualTo("JURISDICTION_SELECTED");

        CareerEntity persisted = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persisted.getCompanyName()).isEqualTo("Texas Haul");
        assertThat(persisted.getStateCode()).isEqualTo("AZ");
        assertThat(persisted.getBaseCity()).isEqualTo("Phoenix, AZ");
        assertThat(ledgerRepository.count()).isZero();

        Phase2TransitionResponse replay = complete(token, career.id(), "ATS", request);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.carrierContract().id()).isEqualTo(completed.carrierContract().id());
        assertThat(carrierContractRepository.count()).isEqualTo(1);
    }

    @Test
    void italyTransitionQuotesAndCreditsSimplifiedTfrThenKeepsCarrierCountrySeparateFromHomeBase() {
        UserEntity owner = saveUser("phase2-it@example.com");
        String token = token(owner);
        CareerResponse career = createCareer(token, "ETS2", "10000.00", "EUR", "IT", null, "Milano");
        unlock(career.id());

        payslipRepository.saveAndFlush(payslip(career.id(), "2700.00"));

        Phase2TransitionResponse pending = get(token, career.id(), "ETS2");
        assertThat(pending.statutorySettlementAmount()).isEqualByComparingTo("200.00");
        assertThat(pending.policySource()).contains("lavoro.gov.it");
        assertThat(pending.terminationCountryCode()).isEqualTo("IT");

        Map<String,Object> request = new LinkedHashMap<>();
        request.put("operationId", UUID.randomUUID());
        request.put("affiliation", "OTHER_COMPANY");
        request.put("carrierName", "Alpine Freight");
        request.put("carrierCountryCode", "DE");
        request.put("effectiveDay", "TUESDAY");
        request.put("expectedBalance", new BigDecimal("10000.00"));

        Phase2TransitionResponse completed = complete(token, career.id(), "ETS2", request);
        assertThat(completed.currentBalance()).isEqualByComparingTo("10200.00");
        assertThat(completed.carrierContract().jurisdictionCountryCode()).isEqualTo("DE");
        assertThat(completed.carrierContract().jurisdictionStateCode()).isNull();
        assertThat(completed.carrierContract().compensationRuleKey()).isEqualTo("ETS2:DE");

        CareerEntity persisted = careerRepository.findById(career.id()).orElseThrow();
        assertThat(persisted.getCountryCode()).isEqualTo("IT");
        assertThat(persisted.getBaseCity()).isEqualTo("Milano");
        assertThat(persisted.getCompanyName()).isEqualTo("Alpine Freight");
        assertThat(ledgerRepository.count()).isEqualTo(1);
    }

    @Test
    void documentsPhase2TransitionEndpoints() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/phase2-transition'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/phase2-transition/complete'].post.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/careers/{careerId}/phase2-transition'].get.security[0].bearerAuth").exists();
    }

    private Phase2TransitionResponse get(String token, UUID careerId, String game) {
        return Objects.requireNonNull(restTestClient.get()
                .uri(path(careerId, game))
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(Phase2TransitionResponse.class)
                .returnResult()
                .getResponseBody());
    }

    private Phase2TransitionResponse complete(String token, UUID careerId, String game, Map<String,Object> request) {
        return Objects.requireNonNull(restTestClient.post()
                .uri(CAREERS + "/" + careerId + "/phase2-transition/complete?game=" + game)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(Phase2TransitionResponse.class)
                .returnResult()
                .getResponseBody());
    }

    private void unlock(UUID careerId) {
        CareerEntity career = careerRepository.findById(careerId).orElseThrow();
        career.unlockPhase2(Instant.now());
        careerRepository.saveAndFlush(career);
    }

    private PayslipEntity payslip(UUID careerId, String gross) {
        BigDecimal amount = new BigDecimal(gross);
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new PayslipEntity(
                UUID.randomUUID(),
                careerId,
                com.luislipinski.trucklife.career.domain.CareerGame.ETS2,
                null,
                1,
                1,
                1,
                (short) 1,
                "EUR",
                amount,
                zero,
                zero,
                zero,
                amount,
                zero,
                amount,
                zero,
                zero,
                amount,
                zero,
                0,
                0,
                0,
                0,
                "{}",
                Instant.now()
        );
    }

    private String path(UUID careerId, String game) {
        return CAREERS + "/" + careerId + "/phase2-transition?game=" + game;
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
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("game", game);
        request.put("driverName", "Truck Owner");
        request.put("companyName", "Road Logistics");
        request.put("initialBalance", new BigDecimal(balance));
        request.put("baseCurrency", currency);
        request.put("displayCurrency", currency);
        request.put("exchangeRate", new BigDecimal("1.00000000"));
        request.put("exchangeRateAsOf", "2026-11-28");
        if (state != null) request.put("stateCode", state);
        if (country != null) request.put("countryCode", country);
        request.put("baseCity", city);
        request.put("cityMarketVersion", "test-v1");
        request.put("cityMarketLabel", "Test market");
        request.put("cityCostFactor", new BigDecimal("1.0000"));
        request.put("citySalaryFactor", new BigDecimal("1.0000"));
        return Objects.requireNonNull(restTestClient.post()
                .uri(CAREERS)
                .headers(h -> h.setBearerAuth(token))
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
