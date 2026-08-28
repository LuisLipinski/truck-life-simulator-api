package com.luislipinski.trucklife.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"info.app.commit=test-commit","info.app.branch=test-branch"})
@AutoConfigureRestTestClient @ActiveProfiles("test") @Testcontainers
class PostgresMigrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    @Autowired JdbcTemplate jdbcTemplate;@Autowired RestTestClient restTestClient;

    @Test void appliesAllDomainMigrationsThroughLedgerV12(){
        Integer tableCount=jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN (
                'platform_metadata','users','refresh_tokens','user_action_tokens','careers','career_events','trips','payroll_periods','payslips','payslip_lines',
                'incidents','incident_payslip_deductions','academy_progress','qualifications','monthly_expenses','monthly_expense_applications','emergency_reserve','emergency_reserve_events','ledger_entries')
                """,Integer.class);
        String latest=jdbcTemplate.queryForObject("SELECT version FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",String.class);
        assertThat(tableCount).isEqualTo(19);assertThat(latest).isEqualTo("12");
        assertThat(columns("careers")).contains("current_operational_week","current_payroll_month","dangerous_goods_qualified");
        assertThat(columns("payslips")).contains("incident_deduction_amount","reserve_interest_amount","reserve_contribution_amount","balance_credit_amount","context_snapshot_json");
        assertThat(columns("monthly_expenses")).contains("career_id","expense_type","category","amount","included","display_currency","policy_version","context_snapshot_json","version");
        assertThat(columns("monthly_expense_applications")).contains("id","career_id","operational_week","payroll_month","amount","context_snapshot_json","applied_at");
        assertThat(columns("emergency_reserve")).contains("career_id","balance","annual_yield_rate","auto_contribution_enabled","auto_contribution_amount","display_currency","version");
        assertThat(columns("emergency_reserve_events")).contains("id","career_id","payslip_id","event_type","amount","balance_before","balance_after","operational_week","payroll_month","reason","recorded_at");
        assertThat(columns("ledger_entries")).contains("id","career_id","entry_type","source_type","source_id","entry_order","operational_week","payroll_month","amount","balance_delta","reserve_delta","balance_before","balance_after","reserve_balance_before","reserve_balance_after","display_currency","description","metadata_json","recorded_at");
        List<String> indexes=jdbcTemplate.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname='public'",String.class);
        assertThat(indexes).contains("idx_careers_user_game_created_at","idx_trips_career_week_created_at","idx_payslips_career_generated_at","idx_incidents_career_recorded_at","idx_academy_progress_career_completed","idx_qualifications_career_acquired","idx_monthly_expenses_career_type","idx_monthly_expense_applications_career_applied","idx_emergency_reserve_events_career_recorded","idx_ledger_entries_career_recorded","idx_ledger_entries_career_type");
        assertThat(constraints("monthly_expenses")).contains("fk_monthly_expenses_career","chk_monthly_expenses_type","chk_monthly_expenses_category","uq_monthly_expenses_career_category");
        assertThat(constraints("emergency_reserve_events")).contains("fk_emergency_reserve_events_career","fk_emergency_reserve_events_payslip","chk_emergency_reserve_events_type","uq_emergency_reserve_payslip_event");
        assertThat(constraints("payslips")).contains("chk_payslips_reserve_interest","chk_payslips_reserve_contribution","chk_payslips_balance_credit");
        assertThat(constraints("ledger_entries")).contains("fk_ledger_entries_career","chk_ledger_entries_type","chk_ledger_entries_source","chk_ledger_entries_balance","chk_ledger_entries_reserve_snapshot","uq_ledger_entries_source");
    }

    @Test void exposesTheAppliedFlywayStateWithoutSensitiveDatabaseData(){
        restTestClient.get().uri("/actuator/info").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.app.commit").isEqualTo("test-commit").jsonPath("$.app.branch").isEqualTo("test-branch")
                .jsonPath("$.databaseSchema.currentVersion").isEqualTo("12").jsonPath("$.databaseSchema.pendingMigrations").isEqualTo(0);
    }

    private List<String> columns(String table){return jdbcTemplate.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position",String.class,table);}
    private List<String> constraints(String table){return jdbcTemplate.queryForList("SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema='public' AND table_name=?",String.class,table);}
}
