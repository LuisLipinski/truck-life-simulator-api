package com.luislipinski.trucklife.career;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
class CareerPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private CareerRepository careerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUpUsers() {
        careerRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");

        ownerId = insertUser("owner@example.com", "Owner");
        otherUserId = insertUser("other@example.com", "Other");
    }

    @Test
    void persistsCareersAndScopesQueriesByOwnerAndGame() {
        CareerEntity olderOwnerAtsCareer = career(
                ownerId,
                CareerGame.ATS,
                "Alex Driver",
                "Phoenix",
                "AZ",
                null,
                Instant.parse("2026-08-25T10:00:00Z")
        );
        CareerEntity newerOwnerAtsCareer = career(
                ownerId,
                CareerGame.ATS,
                "Alex Second",
                "Tucson",
                "AZ",
                null,
                Instant.parse("2026-08-25T12:00:00Z")
        );
        CareerEntity ownerEts2Career = career(
                ownerId,
                CareerGame.ETS2,
                "Alex Europe",
                "Berlin",
                null,
                "DE",
                Instant.parse("2026-08-25T11:00:00Z")
        );
        CareerEntity otherUserAtsCareer = career(
                otherUserId,
                CareerGame.ATS,
                "Other Driver",
                "Seattle",
                "WA",
                null,
                Instant.parse("2026-08-25T09:00:00Z")
        );

        careerRepository.saveAllAndFlush(List.of(
                newerOwnerAtsCareer,
                ownerEts2Career,
                otherUserAtsCareer,
                olderOwnerAtsCareer
        ));

        List<CareerEntity> ownerAtsCareers =
                careerRepository.findAllByUserIdAndGameOrderByCreatedAtAscIdAsc(ownerId, CareerGame.ATS);
        List<CareerEntity> ownerEts2Careers =
                careerRepository.findAllByUserIdAndGameOrderByCreatedAtAscIdAsc(ownerId, CareerGame.ETS2);

        assertThat(ownerAtsCareers)
                .extracting(CareerEntity::getId)
                .containsExactly(olderOwnerAtsCareer.getId(), newerOwnerAtsCareer.getId());
        assertThat(ownerEts2Careers)
                .extracting(CareerEntity::getId)
                .containsExactly(ownerEts2Career.getId());
        assertThat(ownerAtsCareers.get(0).getDefaultTruckMake()).isEqualTo("Kenworth");
        assertThat(ownerAtsCareers.get(0).getDefaultTruckModel()).isEqualTo("T680");
        assertThat(ownerEts2Careers.get(0).getDefaultTruckMake()).isNull();
        assertThat(ownerEts2Careers.get(0).getDefaultTruckModel()).isNull();

        assertThat(careerRepository.findByIdAndUserIdAndGame(
                otherUserAtsCareer.getId(), ownerId, CareerGame.ATS)).isEmpty();
        assertThat(careerRepository.findByIdAndUserIdAndGame(
                ownerEts2Career.getId(), ownerId, CareerGame.ATS)).isEmpty();
        assertThat(careerRepository.findByIdAndUserIdAndGame(
                otherUserAtsCareer.getId(), otherUserId, CareerGame.ATS)).isPresent();
        assertThat(ownerEts2Career.getVersion()).isZero();
    }

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T08:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, email, normalized_email, password_hash, display_name,
                    status, role, email_verified, email_verified_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'USER', TRUE, ?, ?, ?)
                """,
                id,
                email,
                email.toLowerCase(),
                "$argon2id$v=19$m=65536,t=3,p=1$test$test",
                displayName,
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
        return id;
    }

    private CareerEntity career(
            UUID userId,
            CareerGame game,
            String driverName,
            String baseCity,
            String stateCode,
            String countryCode,
            Instant createdAt
    ) {
        return new CareerEntity(
                UUID.randomUUID(),
                userId,
                game,
                driverName,
                "Test Logistics",
                "Persistence integration test career",
                (short) 1,
                new BigDecimal("5000.00"),
                game == CareerGame.ATS ? "USD" : "EUR",
                "BRL",
                new BigDecimal("5.25000000"),
                LocalDate.of(2026, 8, 25),
                stateCode,
                countryCode,
                baseCity,
                game == CareerGame.ATS ? "Kenworth" : null,
                game == CareerGame.ATS ? "T680" : null,
                "test-v1",
                "Test market",
                new BigDecimal("1.0000"),
                new BigDecimal("1.0000"),
                1,
                game == CareerGame.ETS2 ? 1 : null,
                createdAt,
                createdAt
        );
    }
}
