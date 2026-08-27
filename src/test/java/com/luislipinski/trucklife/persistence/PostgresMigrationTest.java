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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
class PostgresMigrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RestTestClient restTestClient;

    @Test
    void appliesTheVersionedFlywayMigrations() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN (
                  'platform_metadata','users','refresh_tokens','user_action_tokens','careers','career_events','trips',
                  'payroll_periods','payslips','payslip_lines','incidents','incident_payslip_deductions',
                  'academy_progress','qualifications')
                """, Integer.class);
        String schemaVersion = jdbcTemplate.queryForObject(
                "SELECT metadata_value FROM platform_metadata WHERE metadata_key='schema_version'",
                String.class
        );
        String latestMigration = jdbcTemplate.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success=TRUE AND version IS NOT NULL
                ORDER BY installed_rank DESC LIMIT 1
                """, String.class);
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname='public' AND tablename IN (
                  'users','refresh_tokens','user_action_tokens','careers','career_events','trips',
                  'payroll_periods','payslips','payslip_lines','incidents','incident_payslip_deductions',
                  'academy_progress','qualifications')
                """, String.class);

        List<String> careerColumns = columns("careers");
        List<String> eventColumns = columns("career_events");
        List<String> tripColumns = columns("trips");
        List<String> payrollPeriodColumns = columns("payroll_periods");
        List<String> payslipColumns = columns("payslips");
        List<String> payslipLineColumns = columns("payslip_lines");
        List<String> incidentColumns = columns("incidents");
        List<String> incidentDeductionColumns = columns("incident_payslip_deductions");
        List<String> academyColumns = columns("academy_progress");
        List<String> qualificationColumns = columns("qualifications");

        List<String> careerConstraints = constraints("careers");
        List<String> payrollPeriodConstraints = constraints("payroll_periods");
        List<String> payslipConstraints = constraints("payslips");
        List<String> payslipLineConstraints = constraints("payslip_lines");
        List<String> incidentConstraints = constraints("incidents");
        List<String> incidentDeductionConstraints = constraints("incident_payslip_deductions");
        List<String> academyConstraints = constraints("academy_progress");
        List<String> qualificationConstraints = constraints("qualifications");

        List<String> currencyColumns = jdbcTemplate.queryForList("""
                SELECT column_name || ':' || data_type || ':' || character_maximum_length
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name='careers'
                  AND column_name IN ('base_currency','display_currency')
                ORDER BY column_name
                """, String.class);

        assertThat(tableCount).isEqualTo(14);
        assertThat(schemaVersion).isEqualTo("1");
        assertThat(latestMigration).isEqualTo("10");
        assertThat(indexes).contains(
                "uq_users_normalized_email",
                "idx_users_status",
                "idx_refresh_tokens_user_id",
                "idx_refresh_tokens_family_id",
                "idx_refresh_tokens_expires_at",
                "idx_user_action_tokens_user_purpose",
                "idx_user_action_tokens_expires_at",
                "idx_careers_user_game_created_at",
                "idx_careers_updated_at",
                "idx_career_events_career_week_recorded_at",
                "idx_trips_career_week_created_at",
                "idx_payroll_periods_career_month_week",
                "idx_payroll_periods_payslip_id",
                "uq_payslips_ats_week",
                "uq_payslips_ets2_month",
                "idx_payslips_career_generated_at",
                "idx_payslip_lines_payslip_order",
                "idx_incidents_career_recorded_at",
                "idx_incidents_pending_payslip",
                "idx_incident_deductions_payslip",
                "idx_academy_progress_career_completed",
                "idx_qualifications_career_acquired"
        );
        assertThat(indexes).doesNotContain("idx_career_events_career_effective_date");

        assertThat(careerColumns).contains(
                "default_truck_make",
                "default_truck_model",
                "current_operational_week",
                "current_payroll_month",
                "dangerous_goods_qualified"
        );
        assertThat(eventColumns).contains(
                "career_id",
                "event_type",
                "operational_week",
                "effective_day",
                "recorded_at",
                "changes_json"
        );
        assertThat(eventColumns).doesNotContain("effective_date");
        assertThat(tripColumns).contains(
                "career_id",
                "operational_week",
                "departure_day",
                "departure_time",
                "arrival_day",
                "arrival_time",
                "official_distance",
                "break_minutes",
                "truck_make",
                "truck_model",
                "odometer_start",
                "odometer_end",
                "source",
                "employer_snapshot_json",
                "base_snapshot_json"
        );
        assertThat(payrollPeriodColumns).contains(
                "id",
                "career_id",
                "operational_week",
                "payroll_month",
                "payslip_id",
                "context_snapshot_json",
                "closed_at"
        );
        assertThat(payslipColumns).contains(
                "id",
                "career_id",
                "game_id",
                "operational_week",
                "payroll_month",
                "start_operational_week",
                "end_operational_week",
                "level",
                "display_currency",
                "gross_amount",
                "tax_amount",
                "benefits_amount",
                "per_diem_amount",
                "net_salary_amount",
                "incident_deduction_amount",
                "deposit_amount",
                "total_distance",
                "elapsed_minutes",
                "break_minutes",
                "worked_minutes",
                "overrun_minutes",
                "context_snapshot_json",
                "generated_at"
        );
        assertThat(payslipLineColumns).contains(
                "id",
                "payslip_id",
                "line_order",
                "code",
                "label",
                "line_type",
                "amount",
                "quantity",
                "rate",
                "metadata_json"
        );
        assertThat(incidentColumns).contains(
                "id",
                "career_id",
                "related_trip_id",
                "operational_week",
                "incident_type",
                "amount",
                "remaining_amount",
                "route_label",
                "description",
                "charge_method",
                "status",
                "recorded_at",
                "updated_at",
                "version"
        );
        assertThat(incidentDeductionColumns).contains(
                "id",
                "incident_id",
                "payslip_id",
                "amount",
                "recorded_at"
        );
        assertThat(academyColumns).contains(
                "id",
                "career_id",
                "target_level",
                "module_code",
                "module_name",
                "required_distance",
                "distance_at_completion",
                "fee_amount",
                "display_currency",
                "operational_week",
                "policy_version",
                "context_snapshot_json",
                "completed_at"
        );
        assertThat(qualificationColumns).contains(
                "id",
                "career_id",
                "qualification_type",
                "qualification_name",
                "fee_amount",
                "display_currency",
                "operational_week",
                "policy_version",
                "context_snapshot_json",
                "acquired_at"
        );

        assertThat(careerConstraints)
                .contains("chk_careers_payroll_month_context")
                .doesNotContain("chk_careers_payroll_month");
        assertThat(payrollPeriodConstraints).contains(
                "fk_payroll_periods_career",
                "fk_payroll_periods_payslip",
                "chk_payroll_periods_operational_week",
                "chk_payroll_periods_payroll_month",
                "uq_payroll_periods_career_week"
        );
        assertThat(payslipConstraints).contains(
                "fk_payslips_career",
                "chk_payslips_game",
                "chk_payslips_period_context",
                "chk_payslips_week_range",
                "chk_payslips_level",
                "chk_payslips_nonnegative_amounts",
                "chk_payslips_minutes",
                "chk_payslips_incident_deduction"
        );
        assertThat(payslipLineConstraints).contains(
                "fk_payslip_lines_payslip",
                "chk_payslip_lines_type",
                "chk_payslip_lines_amount",
                "uq_payslip_lines_order"
        );
        assertThat(incidentConstraints).contains(
                "fk_incidents_career",
                "fk_incidents_related_trip",
                "chk_incidents_operational_week",
                "chk_incidents_type",
                "chk_incidents_amount",
                "chk_incidents_remaining",
                "chk_incidents_charge_method",
                "chk_incidents_status",
                "chk_incidents_charge_state",
                "chk_incidents_pending_state",
                "chk_incidents_partial_state",
                "chk_incidents_closed_state"
        );
        assertThat(incidentDeductionConstraints).contains(
                "fk_incident_deductions_incident",
                "fk_incident_deductions_payslip",
                "chk_incident_deductions_amount",
                "uq_incident_deductions_incident_payslip"
        );
        assertThat(academyConstraints).contains(
                "fk_academy_progress_career",
                "chk_academy_progress_target_level",
                "chk_academy_progress_module",
                "chk_academy_progress_distance",
                "chk_academy_progress_fee",
                "chk_academy_progress_currency",
                "chk_academy_progress_week",
                "chk_academy_progress_snapshot",
                "uq_academy_progress_career_level"
        );
        assertThat(qualificationConstraints).contains(
                "fk_qualifications_career",
                "chk_qualifications_type",
                "chk_qualifications_name",
                "chk_qualifications_fee",
                "chk_qualifications_currency",
                "chk_qualifications_week",
                "chk_qualifications_snapshot",
                "uq_qualifications_career_type"
        );
        assertThat(currencyColumns).containsExactly(
                "base_currency:character varying:3",
                "display_currency:character varying:3"
        );
    }

    @Test
    void exposesHealthAndOpenApiContracts() {
        restTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").exists()
                .jsonPath("$.info.title").isEqualTo("Truck Life Simulator API");
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?",
                String.class,
                table
        );
    }

    private List<String> constraints(String table) {
        return jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid=to_regclass(?)",
                String.class,
                table
        );
    }
}
