package com.luislipinski.trucklife.backup.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
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
class CareerImportValidationIntegrationTest {

    private static final String VALIDATE_PATH = "/api/v1/careers/imports/validate";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired RestTestClient restTestClient;
    @Autowired UserRepository userRepository;
    @Autowired JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void clean() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void requiresAuthentication() {
        restTestClient.post()
                .uri(VALIDATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(validAtsRequest())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void validatesNormalizedAtsSnapshotWithoutPersistingIt() {
        UserEntity owner = saveUser("p4-import-owner@example.com");
        CareerImportValidationRequest request = validAtsRequest();

        CareerImportValidationResponse response = Objects.requireNonNull(
                restTestClient.post()
                        .uri(VALIDATE_PATH)
                        .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                        .expectBody(CareerImportValidationResponse.class)
                        .returnResult()
                        .getResponseBody()
        );

        assertThat(response.operationId()).isEqualTo(request.operationId());
        assertThat(response.sourceCareerId()).isEqualTo(request.sourceCareerId());
        assertThat(response.valid()).isTrue();
        assertThat(response.persisted()).isFalse();
        assertThat(response.summary().driverName()).isEqualTo("Local ATS Driver");
        assertThat(response.summary().currentLevel()).isEqualTo((short) 2);
        assertThat(response.summary().balance()).isEqualByComparingTo("4321.50");
        assertThat(response.summary().currentOperationalWeek()).isEqualTo(7);
        assertThat(response.summary().currentPayrollMonth()).isNull();
        assertThat(response.summary().trips()).isEqualTo(2);
        assertThat(response.summary().closedPeriods()).isEqualTo(1);
        assertThat(response.summary().incidents()).isEqualTo(1);
        assertThat(response.summary().careerEvents()).isEqualTo(1);
        assertThat(response.summary().customExpenses()).isEqualTo(1);
    }

    @Test
    void rejectsMixedGameOrChangedSourceIdentity() {
        UserEntity owner = saveUser("p4-import-invalid@example.com");
        CareerImportValidationRequest original = validAtsRequest();
        Map<String, Object> mixedCareer = new LinkedHashMap<>(original.career());
        mixedCareer.put("gameId", "ets2");

        restTestClient.post()
                .uri(VALIDATE_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CareerImportValidationRequest(
                        original.operationId(),
                        original.sourceCareerId(),
                        original.game(),
                        original.sourceVersion(),
                        mixedCareer,
                        original.state()
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_IMPORT_INVALID");

        restTestClient.post()
                .uri(VALIDATE_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken(owner)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CareerImportValidationRequest(
                        UUID.randomUUID(),
                        "another-local-career",
                        original.game(),
                        original.sourceVersion(),
                        original.career(),
                        original.state()
                ))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CAREER_IMPORT_INVALID");
    }

    @Test
    void exposesValidationEndpointInOpenApi() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/careers/imports/validate'].post").exists();
    }

    private CareerImportValidationRequest validAtsRequest() {
        String sourceCareerId = "career_1720000000000_local01";
        Map<String, Object> career = new LinkedHashMap<>();
        career.put("id", sourceCareerId);
        career.put("gameId", "ats");
        career.put("driverName", "Local ATS Driver");
        career.put("city", "Phoenix, AZ");
        career.put("company", "Local Logistics");
        career.put("stateCode", "AZ");
        career.put("currency", "USD");
        career.put("baseCurrency", "USD");
        career.put("currentBalance", new BigDecimal("4321.50"));
        career.put("currentLevel", 2);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("balance", new BigDecimal("4321.50"));
        state.put("emergencyReserve", new BigDecimal("500.00"));
        state.put("currentLevel", 2);
        state.put("careerLevel", 2);
        state.put("currentWeek", 7);
        state.put("history", List.of(Map.of("type", "BASE_CHANGED")));
        state.put("trips", List.of(
                Map.of("week", 5, "distance", 220, "source", "IMPORT"),
                Map.of("week", 6, "distance", 180, "source", "MANUAL")
        ));
        state.put("closedWeeks", List.of(Map.of("week", 5, "periodType", "week")));
        state.put("customExpenses", List.of(Map.of("name", "Parking", "amount", 40)));
        state.put("incidents", List.of(Map.of("type", "FINE", "amount", 75)));
        state.put("closedOperationalWeeks", List.of(5, 6));
        state.put("expenses", Map.of("rent", 1200));
        state.put("academy", Map.of("level2", true, "level3", false));

        return new CareerImportValidationRequest(
                UUID.randomUUID(),
                sourceCareerId,
                com.luislipinski.trucklife.career.domain.CareerGame.ATS,
                12,
                career,
                state
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

    private String accessToken(UserEntity user) {
        return tokenIssuer.issue(user, UUID.randomUUID()).token();
    }
}
