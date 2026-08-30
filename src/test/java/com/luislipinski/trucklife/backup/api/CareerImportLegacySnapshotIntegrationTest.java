package com.luislipinski.trucklife.backup.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.backup.application.CareerImportService;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationRepository;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CareerImportLegacySnapshotIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired CareerImportService importService;
    @Autowired CareerImportOperationRepository importRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        importRepository.deleteAllInBatch();
        careerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void importsLegacyAtsSnapshotWithoutRequiringCalendarDatesInGameplayTables() {
        UserEntity owner = saveUser("p4-legacy-real-career@example.com");
        CareerImportValidationRequest request = legacyAtsRequest();

        CareerImportResponse response = importService.importCareer(owner.getId(), request);

        assertThat(response.persisted()).isTrue();
        assertThat(response.idempotentReplay()).isFalse();

        Map<String, Object> trip = jdbc.queryForMap("""
                SELECT operational_week, departure_day, departure_time::text AS departure_time,
                       arrival_day, arrival_time::text AS arrival_time,
                       origin_city, origin_company, destination_city, destination_company,
                       official_distance
                FROM trips WHERE career_id=?
                """, response.careerId());
        assertThat(trip.get("operational_week")).isEqualTo(1);
        assertThat(trip.get("departure_day")).isEqualTo("MONDAY");
        assertThat(trip.get("departure_time")).isEqualTo("08:15:00");
        assertThat(trip.get("arrival_day")).isEqualTo("MONDAY");
        assertThat(trip.get("arrival_time")).isEqualTo("12:45:00");
        assertThat(trip.get("origin_city")).isEqualTo("Phoenix, AZ");
        assertThat(trip.get("origin_company")).isEqualTo("Legacy Freight");
        assertThat(trip.get("destination_city")).isEqualTo("Tucson, AZ");
        assertThat(trip.get("destination_company")).isEqualTo("Customer");
        assertThat((BigDecimal) trip.get("official_distance")).isEqualByComparingTo("120.00");

        Map<String, Object> payslip = jdbc.queryForMap("""
                SELECT gross_amount, tax_amount, benefits_amount, per_diem_amount, net_salary_amount,
                       incident_deduction_amount, deposit_amount, total_distance
                FROM payslips WHERE career_id=?
                """, response.careerId());
        assertThat((BigDecimal) payslip.get("gross_amount")).isEqualByComparingTo("850.00");
        assertThat((BigDecimal) payslip.get("tax_amount")).isEqualByComparingTo("220.00");
        assertThat((BigDecimal) payslip.get("benefits_amount")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) payslip.get("per_diem_amount")).isEqualByComparingTo("80.00");
        assertThat((BigDecimal) payslip.get("net_salary_amount")).isEqualByComparingTo("630.00");
        assertThat((BigDecimal) payslip.get("incident_deduction_amount")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) payslip.get("deposit_amount")).isEqualByComparingTo("700.00");
        assertThat((BigDecimal) payslip.get("total_distance")).isEqualByComparingTo("120.00");

        Map<String, Object> incident = jdbc.queryForMap("""
                SELECT charge_method, status FROM incidents WHERE career_id=?
                """, response.careerId());
        assertThat(incident.get("charge_method")).isEqualTo("PAYSLIP");
        assertThat(incident.get("status")).isEqualTo("PENDING_PAYSLIP");

        String archive = jdbc.queryForObject(
                "SELECT snapshot_json FROM career_import_archives WHERE career_id=?",
                String.class,
                response.careerId()
        );
        assertThat(archive)
                .contains("2026-05-04T08:15")
                .contains("\"from\":\"Phoenix, AZ\"")
                .contains("\"netDeposit\":700");

        CareerImportResponse replay = importService.importCareer(owner.getId(), request);
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(replay.careerId()).isEqualTo(response.careerId());
        assertThat(careerRepository.count()).isEqualTo(1);
        assertThat(importRepository.count()).isEqualTo(1);
    }

    private CareerImportValidationRequest legacyAtsRequest() {
        String sourceId = "legacy_real_ats_v12";

        Map<String, Object> career = new LinkedHashMap<>();
        career.put("id", sourceId);
        career.put("gameId", "ats");
        career.put("driverName", "Legacy Driver");
        career.put("city", "Phoenix, AZ");
        career.put("company", "Legacy Freight");
        career.put("bio", "Existing local career");
        career.put("stateCode", "AZ");
        career.put("baseCurrency", "USD");
        career.put("currency", "USD");
        career.put("exchangeRate", new BigDecimal("1.0"));
        career.put("exchangeRateAsOf", "2026-05-01");
        career.put("cityMarketVersion", "ats-city-market-v1");
        career.put("cityMarketLabel", "Phoenix reference");
        career.put("cityCostFactor", new BigDecimal("1.0500"));
        career.put("citySalaryFactor", new BigDecimal("1.0300"));
        career.put("currentBalance", new BigDecimal("4321.25"));
        career.put("currentLevel", 1);

        Map<String, Object> trip = new LinkedHashMap<>();
        trip.put("id", "trip_legacy_1");
        trip.put("week", 1);
        trip.put("date", "2026-05-04");
        trip.put("departureAt", "2026-05-04T08:15");
        trip.put("arrivalAt", "2026-05-04T12:45");
        trip.put("from", "Phoenix, AZ");
        trip.put("fromCompany", "Legacy Freight");
        trip.put("to", "Tucson, AZ");
        trip.put("toCompany", "Customer");
        trip.put("cargo", "Food");
        trip.put("type", "Loaded");
        trip.put("payCategory", "normal");
        trip.put("miles", new BigDecimal("120"));

        Map<String, Object> closedWeek = new LinkedHashMap<>();
        closedWeek.put("week", 1);
        closedWeek.put("closedAt", "04/05/2026, 13:00:00");
        closedWeek.put("trips", List.of(trip));
        closedWeek.put("totalMiles", new BigDecimal("120"));
        closedWeek.put("gross", new BigDecimal("850.00"));
        closedWeek.put("perDiem", new BigDecimal("80.00"));
        closedWeek.put("incidentDeduction", new BigDecimal("10.00"));
        closedWeek.put("netDeposit", new BigDecimal("700.00"));

        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("id", "inc_legacy_1");
        incident.put("type", "infraction");
        incident.put("amount", new BigDecimal("100.00"));
        incident.put("date", "2026-05-04");
        incident.put("time", "12:30");
        incident.put("route", "I-10");
        incident.put("description", "Legacy fine");
        incident.put("payment", "payslip");
        incident.put("status", "pending");
        incident.put("createdAt", "2026-05-04T13:00:00Z");

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("balance", new BigDecimal("4321.25"));
        state.put("emergencyReserve", BigDecimal.ZERO);
        state.put("currentLevel", 1);
        state.put("careerLevel", 1);
        state.put("currentWeek", 2);
        state.put("history", List.of(Map.of("type", "legacy-note")));
        state.put("trips", List.of(trip));
        state.put("closedWeeks", List.of(closedWeek));
        state.put("customExpenses", List.of());
        state.put("incidents", List.of(incident));
        state.put("closedOperationalWeeks", List.of(1));
        state.put("expenses", Map.of());
        state.put("academy", Map.of("level2", false, "level3", false));
        state.put("hazmatQualified", false);

        return new CareerImportValidationRequest(
                UUID.randomUUID(),
                sourceId,
                CareerGame.ATS,
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
}
