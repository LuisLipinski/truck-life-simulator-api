package com.luislipinski.trucklife.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FinancialLedgerBackfillMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Test
    void reconstructsExistingBalanceAndReserveHistoryWhenUpgradingFromV11ToV12() {
        migrateTo("11");
        JdbcTemplate jdbc = jdbc();
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID careerId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID payslipId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID expenseApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID reserveDepositId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID reserveInterestId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID reserveAutoId = UUID.fromString("00000000-0000-0000-0000-000000000503");

        jdbc.update("""
                INSERT INTO users (id,email,normalized_email,password_hash,display_name,status,role,email_verified,email_verified_at,created_at,updated_at)
                VALUES (?,?,?,?,?,'ACTIVE','USER',TRUE,?::timestamptz,?::timestamptz,?::timestamptz)
                """, userId, "backfill@example.com", "backfill@example.com", "unused-password-hash", "Backfill Driver",
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
        jdbc.update("""
                INSERT INTO careers (id,user_id,game_id,driver_name,company_name,current_level,balance,base_currency,display_currency,
                    exchange_rate,state_code,base_city,city_market_version,city_market_label,city_cost_factor,city_salary_factor,
                    current_operational_week,current_payroll_month,created_at,updated_at,version,dangerous_goods_qualified)
                VALUES (?,?,'ATS','Backfill Driver','Road Logistics',1,1300.00,'USD','USD',1.00000000,'AZ','Phoenix, AZ',
                    'test-v1','Test market',1.0000,1.0000,2,NULL,?::timestamptz,?::timestamptz,0,FALSE)
                """, careerId, userId, "2026-01-01T00:00:00Z", "2026-01-01T04:00:00Z");
        jdbc.update("""
                INSERT INTO monthly_expense_applications (id,career_id,operational_week,payroll_month,amount,display_currency,context_snapshot_json,applied_at)
                VALUES (?,?,1,NULL,100.00,'USD','{"backfill":"expense"}',?::timestamptz)
                """, expenseApplicationId, careerId, "2026-01-01T01:00:00Z");
        jdbc.update("""
                INSERT INTO payslips (id,career_id,game_id,operational_week,payroll_month,start_operational_week,end_operational_week,level,
                    display_currency,gross_amount,tax_amount,benefits_amount,per_diem_amount,net_salary_amount,incident_deduction_amount,
                    deposit_amount,reserve_interest_amount,reserve_contribution_amount,balance_credit_amount,total_distance,elapsed_minutes,
                    break_minutes,worked_minutes,overrun_minutes,context_snapshot_json,generated_at)
                VALUES (?,?,'ATS',1,NULL,1,1,1,'USD',510.00,0.00,0.00,0.00,510.00,0.00,510.00,5.00,10.00,500.00,
                    0.00,0,0,0,0,'{"backfill":"payslip"}',?::timestamptz)
                """, payslipId, careerId, "2026-01-01T03:00:00Z");
        jdbc.update("""
                INSERT INTO emergency_reserve (career_id,balance,annual_yield_rate,auto_contribution_enabled,auto_contribution_amount,
                    display_currency,policy_version,updated_at,version)
                VALUES (?,115.00,0.032500,TRUE,10.00,'USD','phase1-finance-2026-v1',?::timestamptz,0)
                """, careerId, "2026-01-01T03:00:00Z");
        jdbc.update("""
                INSERT INTO emergency_reserve_events (id,career_id,payslip_id,event_type,amount,balance_before,balance_after,
                    display_currency,operational_week,payroll_month,reason,recorded_at)
                VALUES (?,?,NULL,'MANUAL_DEPOSIT',100.00,0.00,100.00,'USD',1,NULL,NULL,?::timestamptz)
                """, reserveDepositId, careerId, "2026-01-01T02:00:00Z");
        jdbc.update("""
                INSERT INTO emergency_reserve_events (id,career_id,payslip_id,event_type,amount,balance_before,balance_after,
                    display_currency,operational_week,payroll_month,reason,recorded_at)
                VALUES (?,?,?,'INTEREST',5.00,100.00,105.00,'USD',1,NULL,NULL,?::timestamptz)
                """, reserveInterestId, careerId, payslipId, "2026-01-01T03:00:00Z");
        jdbc.update("""
                INSERT INTO emergency_reserve_events (id,career_id,payslip_id,event_type,amount,balance_before,balance_after,
                    display_currency,operational_week,payroll_month,reason,recorded_at)
                VALUES (?,?,?,'AUTO_CONTRIBUTION',10.00,105.00,115.00,'USD',1,NULL,NULL,?::timestamptz)
                """, reserveAutoId, careerId, payslipId, "2026-01-01T03:00:00Z");

        migrateTo("12");

        String latest = jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class);
        List<String> types = jdbc.queryForList("SELECT entry_type FROM ledger_entries WHERE career_id=? ORDER BY recorded_at,entry_order,entry_type", String.class, careerId);
        BigDecimal opening = jdbc.queryForObject("SELECT balance_after FROM ledger_entries WHERE career_id=? AND entry_type='OPENING_BALANCE'", BigDecimal.class, careerId);
        BigDecimal reconstructedBalance = jdbc.queryForObject("SELECT SUM(balance_delta) FROM ledger_entries WHERE career_id=?", BigDecimal.class, careerId);
        BigDecimal reconstructedReserve = jdbc.queryForObject("SELECT SUM(reserve_delta) FROM ledger_entries WHERE career_id=?", BigDecimal.class, careerId);
        BigDecimal lastBalance = jdbc.queryForObject("SELECT balance_after FROM ledger_entries WHERE career_id=? ORDER BY recorded_at DESC,entry_order DESC,id DESC LIMIT 1", BigDecimal.class, careerId);

        assertThat(latest).isEqualTo("12");
        assertThat(types).containsExactly("OPENING_BALANCE","MONTHLY_EXPENSE","RESERVE_DEPOSIT","RESERVE_INTEREST","RESERVE_AUTO_CONTRIBUTION","PAYSLIP_CREDIT");
        assertThat(opening).isEqualByComparingTo("1000.00");
        assertThat(reconstructedBalance).isEqualByComparingTo("1300.00");
        assertThat(lastBalance).isEqualByComparingTo("1300.00");
        assertThat(reconstructedReserve).isEqualByComparingTo("115.00");
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion(target))
                .load()
                .migrate();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }
}
