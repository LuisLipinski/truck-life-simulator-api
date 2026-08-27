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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
class PostgresMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void appliesTheVersionedFlywayMigrations() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'platform_metadata',
                    'users',
                    'refresh_tokens',
                    'user_action_tokens',
                    'careers',
                    'career_events',
                    'trips'
                  )
                """,
                Integer.class
        );
        String schemaVersion = jdbcTemplate.queryForObject(
                "SELECT metadata_value FROM platform_metadata WHERE metadata_key = 'schema_version'",
                String.class
        );

        String latestMigration = jdbcTemplate.queryForObject(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE AND version IS NOT NULL
                ORDER BY installed_rank DESC
                LIMIT 1
                """,
                String.class
        );
        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename IN ('users', 'refresh_tokens', 'user_action_tokens', 'careers', 'career_events', 'trips')
                """,
                String.class
        );
        List<String> careerColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'careers'
                """,
                String.class
        );
        List<String> eventColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'career_events'
                """,
                String.class
        );
        List<String> tripColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'trips'
                """,
                String.class
        );
        List<String> currencyColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name || ':' || data_type || ':' || character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'careers'
                  AND column_name IN ('base_currency', 'display_currency')
                ORDER BY column_name
                """,
                String.class
        );

        assertThat(tableCount).isEqualTo(7);
        assertThat(schemaVersion).isEqualTo("1");
        assertThat(latestMigration).isEqualTo("6");
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
                "idx_trips_career_week_created_at"
        );
        assertThat(indexes).doesNotContain("idx_career_events_career_effective_date");
        assertThat(careerColumns).contains(
                "default_truck_make",
                "default_truck_model"
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
}
