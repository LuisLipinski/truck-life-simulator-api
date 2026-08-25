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
                    'user_action_tokens'
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
        List<String> identityIndexes = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename IN ('users', 'refresh_tokens', 'user_action_tokens')
                """,
                String.class
        );

        assertThat(tableCount).isEqualTo(4);
        assertThat(schemaVersion).isEqualTo("1");
        assertThat(latestMigration).isEqualTo("2");
        assertThat(identityIndexes).contains(
                "uq_users_normalized_email",
                "idx_users_status",
                "idx_refresh_tokens_user_id",
                "idx_refresh_tokens_family_id",
                "idx_refresh_tokens_expires_at",
                "idx_user_action_tokens_user_purpose",
                "idx_user_action_tokens_expires_at"
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
