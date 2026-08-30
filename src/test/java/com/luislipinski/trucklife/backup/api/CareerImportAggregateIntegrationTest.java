package com.luislipinski.trucklife.backup.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.backup.application.CareerImportService;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationRepository;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
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
class CareerImportAggregateIntegrationTest {

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
    void importsAtsHistoricalAggregateAtMigrationBoundaryWithoutRecalculatingClosedValues() {
        UserEntity owner = saveUser("p4-aggregate-ats@example.com");
        CareerImportValidationRequest request = atsHistoricalRequest();

        CareerImportResponse created = importService.importCareer(owner.getId(), request);
        CareerEntity career = careerRepository.findById(created.careerId()).orElseThrow();

        assertThat(created.persisted()).isTrue();
        assertThat(created.idempotentReplay()).isFalse();
        assertThat(career.getCurrentLevel()).isEqualTo((short) 2);
        assertThat(career.getCurrentOperationalWeek()).isEqualTo(4);
        assertThat(career.getCurrentPayrollMonth()).isNull();
        assertThat(career.isDangerousGoodsQualified()).isTrue();
        assertThat(career.getBalance()).isEqualByComparingTo("4321.25");

        assertCount("career_import_archives", created.careerId(), 1);
        assertCount("trips", created.careerId(), 1);
        assertCount("payroll_periods", created.careerId(), 1);
        assertCount("payslips", created.careerId(), 1);
        assertCount("incidents", created.careerId(), 1);
        assertCount("monthly_expenses", created.careerId(), 4);
        assertCount("emergency_reserve", created.careerId(), 1);
        assertCount("ledger_entries", created.careerId(), 1);

        BigDecimal gross = jdbc.queryForObject(
                "SELECT gross_amount FROM payslips WHERE career_id=?",
                BigDecimal.class,
                created.careerId()
        );
        BigDecimal deposit = jdbc.queryForObject(
                "SELECT deposit_amount FROM payslips WHERE career_id=?",
                BigDecimal.class,
                created.careerId()
        );
        BigDecimal reserveContribution = jdbc.queryForObject(
                "SELECT reserve_contribution_amount FROM payslips WHERE career_id=?",
                BigDecimal.class,
                created.careerId()
        );
        assertThat(gross).isEqualByComparingTo("1000.00");
        assertThat(deposit).isEqualByComparingTo("884.00");
        assertThat(reserveContribution).isEqualByComparingTo("0.00");

        Map<String, Object> opening = jdbc.queryForMap("""
                SELECT operational_week, amount, balance_before, balance_after,
                       reserve_delta, reserve_balance_before, reserve_balance_after
                FROM ledger_entries WHERE career_id=? AND entry_type='OPENING_BALANCE'
                """, created.careerId());
        assertThat(opening.get("operational_week")).isEqualTo(4);
        assertThat((BigDecimal) opening.get("amount")).isEqualByComparingTo("4321.25");
        assertThat((BigDecimal) opening.get("balance_before")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) opening.get("balance_after")).isEqualByComparingTo("4321.25");
        assertThat((BigDecimal) opening.get("reserve_delta")).isEqualByComparingTo("500.00");
        assertThat((BigDecimal) opening.get("reserve_balance_before")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) opening.get("reserve_balance_after")).isEqualByComparingTo("500.00");

        Map<String, Object> reserve = jdbc.queryForMap("""
                SELECT balance, auto_contribution_enabled, auto_contribution_amount
                FROM emergency_reserve WHERE career_id=?
                """, created.careerId());
        assertThat((BigDecimal) reserve.get("balance")).isEqualByComparingTo("500.00");
        assertThat(reserve.get("auto_contribution_enabled")).isEqualTo(true);
        assertThat((BigDecimal) reserve.get("auto_contribution_amount")).isEqualByComparingTo("50.00");

        String archive = jdbc.queryForObject(
                "SELECT snapshot_json FROM career_import_archives WHERE career_id=?",
                String.class,
                created.careerId()
        );
        assertThat(archive).contains("legacy-note").contains("sourceVersion").contains("closedWeeks");

        CareerImportResponse replay = importService.importCareer(owner.getId(), request);
        assertThat(replay.idempotentReplay()).isTrue();
        assertThat(replay.careerId()).isEqualTo(created.careerId());
        assertThat(careerRepository.count()).isEqualTo(1);
        assertThat(importRepository.count()).isEqualTo(1);
        assertCount("career_import_archives", created.careerId(), 1);
        assertCount("trips", created.careerId(), 1);
        assertCount("payslips", created.careerId(), 1);
        assertCount("ledger_entries", created.careerId(), 1);
    }

    @Test
    void importsEts2PaidMonthAndKeepsCurrentMonthClosedWeeksUnpaid() {
        UserEntity owner = saveUser("p4-aggregate-ets2@example.com");
        CareerImportValidationRequest request = ets2HistoricalRequest();

        CareerImportResponse created = importService.importCareer(owner.getId(), request);
        CareerEntity career = careerRepository.findById(created.careerId()).orElseThrow();

        assertThat(career.getGame()).isEqualTo(CareerGame.ETS2);
        assertThat(career.getCurrentOperationalWeek()).isEqualTo(7);
        assertThat(career.getCurrentPayrollMonth()).isEqualTo(2);
        assertThat(career.getCountryCode()).isEqualTo("DE");
        assertThat(career.getStateCode()).isNull();

        Integer totalPeriods = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payroll_periods WHERE career_id=?",
                Integer.class,
                created.careerId()
        );
        Integer paidMonthOne = jdbc.queryForObject("""
                SELECT COUNT(*) FROM payroll_periods
                WHERE career_id=? AND payroll_month=1 AND payslip_id IS NOT NULL
                """, Integer.class, created.careerId());
        Integer currentMonthPending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM payroll_periods
                WHERE career_id=? AND payroll_month=2 AND payslip_id IS NULL
                """, Integer.class, created.careerId());
        assertThat(totalPeriods).isEqualTo(6);
        assertThat(paidMonthOne).isEqualTo(4);
        assertThat(currentMonthPending).isEqualTo(2);

        Map<String, Object> payslip = jdbc.queryForMap("""
                SELECT operational_week, payroll_month, start_operational_week, end_operational_week,
                       gross_amount, deposit_amount, total_distance
                FROM payslips WHERE career_id=?
                """, created.careerId());
        assertThat(payslip.get("operational_week")).isNull();
        assertThat(payslip.get("payroll_month")).isEqualTo(1);
        assertThat(payslip.get("start_operational_week")).isEqualTo(1);
        assertThat(payslip.get("end_operational_week")).isEqualTo(4);
        assertThat((BigDecimal) payslip.get("gross_amount")).isEqualByComparingTo("3200.00");
        assertThat((BigDecimal) payslip.get("deposit_amount")).isEqualByComparingTo("2420.00");
        assertThat((BigDecimal) payslip.get("total_distance")).isEqualByComparingTo("2800.00");

        Integer openingWeek = jdbc.queryForObject(
                "SELECT operational_week FROM ledger_entries WHERE career_id=? AND entry_type='OPENING_BALANCE'",
                Integer.class,
                created.careerId()
        );
        Integer openingMonth = jdbc.queryForObject(
                "SELECT payroll_month FROM ledger_entries WHERE career_id=? AND entry_type='OPENING_BALANCE'",
                Integer.class,
                created.careerId()
        );
        assertThat(openingWeek).isEqualTo(7);
        assertThat(openingMonth).isEqualTo(2);
    }

    private CareerImportValidationRequest atsHistoricalRequest() {
        String sourceId = "ats_local_history_v12";
        Map<String, Object> career = baseCareer(sourceId, "ats", "Phoenix, AZ", "Legacy ATS Logistics", "USD");
        career.put("stateCode", "AZ");
        career.put("currentBalance", new BigDecimal("4321.25"));
        career.put("currentLevel", 2);

        Map<String, Object> trip = new LinkedHashMap<>();
        trip.put("id", 1001);
        trip.put("week", 2);
        trip.put("departureDay", "monday");
        trip.put("departureTime", "08:00");
        trip.put("arrivalDay", "monday");
        trip.put("arrivalTime", "12:00");
        trip.put("origin", "Phoenix, AZ");
        trip.put("originCompany", "Legacy ATS Logistics");
        trip.put("destination", "Tucson, AZ");
        trip.put("destinationCompany", "Customer");
        trip.put("cargo", "Chemicals");
        trip.put("type", "Loaded");
        trip.put("payCategory", "hazmat");
        trip.put("distance", new BigDecimal("120"));
        trip.put("source", "MANUAL");
        trip.put("truckMake", "Kenworth");
        trip.put("truckModel", "T680");
        trip.put("odometerStart", new BigDecimal("1000.0"));
        trip.put("odometerEnd", new BigDecimal("1125.0"));
        trip.put("createdAt", "2026-05-01T12:00:00Z");

        Map<String, Object> closed = new LinkedHashMap<>();
        closed.put("periodType", "week");
        closed.put("week", 2);
        closed.put("weeks", List.of(2));
        closed.put("startWeek", 2);
        closed.put("endWeek", 2);
        closed.put("level", 2);
        closed.put("gross", new BigDecimal("1000.00"));
        closed.put("taxes", new BigDecimal("150.00"));
        closed.put("taxBreakdown", Map.of("Federal", new BigDecimal("150.00")));
        closed.put("benefits", new BigDecimal("36.00"));
        closed.put("netSalary", new BigDecimal("814.00"));
        closed.put("perDiem", new BigDecimal("80.00"));
        closed.put("incidentDeduction", new BigDecimal("10.00"));
        closed.put("reserveInterest", new BigDecimal("1.25"));
        closed.put("deposit", new BigDecimal("884.00"));
        closed.put("distance", new BigDecimal("120.00"));
        closed.put("routeElapsedMinutes", 240);
        closed.put("routeBreakMinutes", 0);
        closed.put("routeWorkedMinutes", 240);
        closed.put("routeOverrunHours", new BigDecimal("0"));
        closed.put("currency", "USD");
        closed.put("closedAt", "2026-05-01T13:00:00Z");

        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("id", 2001);
        incident.put("week", 3);
        incident.put("type", "Infração");
        incident.put("amount", new BigDecimal("100.00"));
        incident.put("remaining", new BigDecimal("40.00"));
        incident.put("route", "I-10 Phoenix → Tucson");
        incident.put("description", "Legacy incident");
        incident.put("chargeMethod", "payslip");
        incident.put("status", "Parcialmente descontado");

        Map<String, Object> customExpense = new LinkedHashMap<>();
        customExpense.put("name", "Parking");
        customExpense.put("value", new BigDecimal("45.00"));
        customExpense.put("monthly", true);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("balance", new BigDecimal("4321.25"));
        state.put("emergencyReserve", new BigDecimal("500.00"));
        state.put("autoReserveContribution", Map.of("enabled", true, "amount", new BigDecimal("50.00")));
        state.put("currentLevel", 2);
        state.put("careerLevel", 2);
        state.put("currentWeek", 4);
        state.put("history", List.of(Map.of("type", "legacy-note", "value", 123)));
        state.put("trips", List.of(trip));
        state.put("closedWeeks", List.of(closed));
        state.put("customExpenses", List.of(customExpense));
        state.put("incidents", List.of(incident));
        state.put("closedOperationalWeeks", List.of(2));
        state.put("expenses", Map.of(
                "rent", new BigDecimal("900.00"),
                "electricity", new BigDecimal("120.00"),
                "groceries", new BigDecimal("350.00")
        ));
        state.put("academy", Map.of("level2", true, "level3", false));
        state.put("dangerousGoodsQualified", true);

        return new CareerImportValidationRequest(UUID.randomUUID(), sourceId, CareerGame.ATS, 12, career, state);
    }

    private CareerImportValidationRequest ets2HistoricalRequest() {
        String sourceId = "ets2_local_history_v12";
        Map<String, Object> career = baseCareer(sourceId, "ets2", "Berlin", "Legacy Euro Logistics", "EUR");
        career.put("countryCode", "DE");
        career.put("currentBalance", new BigDecimal("6100.00"));
        career.put("currentLevel", 3);

        Map<String, Object> trip = new LinkedHashMap<>();
        trip.put("id", 3001);
        trip.put("week", 4);
        trip.put("departureDay", "tuesday");
        trip.put("departureTime", "06:30");
        trip.put("arrivalDay", "tuesday");
        trip.put("arrivalTime", "11:30");
        trip.put("origin", "Berlin");
        trip.put("destination", "Hamburg");
        trip.put("type", "Loaded");
        trip.put("payCategory", "normal");
        trip.put("distance", new BigDecimal("2800.00"));
        trip.put("breakMinutes", 45);
        trip.put("source", "MANUAL");

        Map<String, Object> closed = new LinkedHashMap<>();
        closed.put("periodType", "month");
        closed.put("month", 1);
        closed.put("weeks", List.of(1, 2, 3, 4));
        closed.put("startWeek", 1);
        closed.put("endWeek", 4);
        closed.put("level", 3);
        closed.put("gross", new BigDecimal("3200.00"));
        closed.put("taxes", new BigDecimal("700.00"));
        closed.put("benefits", new BigDecimal("80.00"));
        closed.put("netSalary", new BigDecimal("2420.00"));
        closed.put("perDiem", BigDecimal.ZERO);
        closed.put("incidentDeduction", BigDecimal.ZERO);
        closed.put("reserveInterest", new BigDecimal("2.00"));
        closed.put("deposit", new BigDecimal("2420.00"));
        closed.put("distance", new BigDecimal("2800.00"));
        closed.put("routeElapsedMinutes", 300);
        closed.put("routeBreakMinutes", 45);
        closed.put("routeWorkedMinutes", 255);
        closed.put("routeOverrunHours", BigDecimal.ZERO);
        closed.put("currency", "EUR");

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("balance", new BigDecimal("6100.00"));
        state.put("emergencyReserve", new BigDecimal("750.00"));
        state.put("currentLevel", 3);
        state.put("careerLevel", 3);
        state.put("currentWeek", 7);
        state.put("currentPayrollMonth", 2);
        state.put("payPeriodStartWeek", 5);
        state.put("history", List.of());
        state.put("trips", List.of(trip));
        state.put("closedWeeks", List.of(closed));
        state.put("customExpenses", List.of());
        state.put("incidents", List.of());
        state.put("closedOperationalWeeks", List.of(1, 2, 3, 4, 5, 6));
        state.put("expenses", Map.of());
        state.put("academy", Map.of("level2", true, "level3", true));
        state.put("dangerousGoodsQualified", false);

        return new CareerImportValidationRequest(UUID.randomUUID(), sourceId, CareerGame.ETS2, 12, career, state);
    }

    private Map<String, Object> baseCareer(
            String sourceId,
            String gameId,
            String city,
            String company,
            String currency
    ) {
        Map<String, Object> career = new LinkedHashMap<>();
        career.put("id", sourceId);
        career.put("gameId", gameId);
        career.put("driverName", "Imported Driver");
        career.put("city", city);
        career.put("company", company);
        career.put("bio", "Historical local career");
        career.put("baseCurrency", currency);
        career.put("currency", currency);
        career.put("exchangeRate", BigDecimal.ONE);
        career.put("exchangeRateAsOf", "2026-05-01");
        career.put("cityMarketVersion", "legacy-city-market-v12");
        career.put("cityMarketLabel", "Imported city profile");
        career.put("cityCostFactor", BigDecimal.ONE);
        career.put("citySalaryFactor", BigDecimal.ONE);
        return career;
    }

    private void assertCount(String table, UUID careerId, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE career_id=?",
                Integer.class,
                careerId
        );
        assertThat(count).isEqualTo(expected);
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
