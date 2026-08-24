package com.luislipinski.trucklife.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.identity.domain.UserActionTokenPurpose;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IdentityPersistenceTest {

    private static final String PASSWORD_HASH = "{argon2}persisted-password-hash";
    private static final String REFRESH_HASH = "a".repeat(64);
    private static final String ROTATED_REFRESH_HASH = "b".repeat(64);
    private static final String ACTION_HASH = "c".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserActionTokenRepository userActionTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsUsersRefreshRotationAndActionTokensThroughIdentityRepositories() {
        Instant now = Instant.parse("2026-08-22T17:00:00Z");
        UserEntity user = userRepository.save(newUser(
                "Driver@example.com",
                "driver@example.com",
                now
        ));

        UUID familyId = UUID.randomUUID();
        RefreshTokenEntity original = refreshTokenRepository.save(new RefreshTokenEntity(
                UUID.randomUUID(),
                user,
                familyId,
                null,
                REFRESH_HASH,
                now,
                now.plus(30, ChronoUnit.DAYS),
                null,
                null,
                "203.0.113.10",
                "Truck Life integration test"
        ));
        RefreshTokenEntity rotated = refreshTokenRepository.save(new RefreshTokenEntity(
                UUID.randomUUID(),
                user,
                familyId,
                original,
                ROTATED_REFRESH_HASH,
                now.plusSeconds(30),
                now.plus(30, ChronoUnit.DAYS),
                null,
                null,
                "203.0.113.10",
                "Truck Life integration test"
        ));
        original.markReplacedBy(rotated, now.plusSeconds(30));
        refreshTokenRepository.save(original);

        UserActionTokenEntity actionToken = userActionTokenRepository.save(
                new UserActionTokenEntity(
                        UUID.randomUUID(),
                        user,
                        UserActionTokenPurpose.EMAIL_VERIFICATION,
                        ACTION_HASH,
                        now.plus(24, ChronoUnit.HOURS),
                        null,
                        now
                )
        );

        entityManager.flush();
        entityManager.clear();

        UserEntity persistedUser = userRepository.findByNormalizedEmail("driver@example.com")
                .orElseThrow();
        RefreshTokenEntity persistedOriginal = refreshTokenRepository.findByTokenHash(REFRESH_HASH)
                .orElseThrow();
        RefreshTokenEntity persistedRotated = refreshTokenRepository
                .findByTokenHash(ROTATED_REFRESH_HASH)
                .orElseThrow();
        UserActionTokenEntity persistedAction = userActionTokenRepository
                .findByTokenHash(ACTION_HASH)
                .orElseThrow();

        assertThat(persistedUser.getId()).isEqualTo(user.getId());
        assertThat(persistedUser.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(persistedOriginal.getReplacedBy().getId()).isEqualTo(rotated.getId());
        assertThat(persistedOriginal.getRevokedAt()).isEqualTo(now.plusSeconds(30));
        assertThat(persistedRotated.getParent().getId()).isEqualTo(original.getId());
        assertThat(persistedRotated.getUser().getId()).isEqualTo(user.getId());
        assertThat(persistedRotated.getFamilyId()).isEqualTo(familyId);
        assertThat(persistedAction.getId()).isEqualTo(actionToken.getId());
        assertThat(persistedAction.getUser().getId()).isEqualTo(user.getId());
        assertThat(persistedAction.getPurpose())
                .isEqualTo(UserActionTokenPurpose.EMAIL_VERIFICATION);
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        Instant now = Instant.now();
        userRepository.saveAndFlush(newUser(
                "Unique@example.com",
                "unique@example.com",
                now
        ));

        UserEntity duplicate = newUser(
                "UNIQUE@example.com",
                "unique@example.com",
                now
        );

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRefreshTokenForUnknownUser() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO refresh_tokens (
                    id, user_id, family_id, token_hash, issued_at, expires_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "d".repeat(64)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnsupportedStatusAndInconsistentVerificationState() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> insertUser(
                "unknown-status@example.com",
                "UNKNOWN",
                false,
                null,
                now
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertUser(
                "invalid-verification@example.com",
                "ACTIVE",
                true,
                null,
                now
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRawTokensAndInvalidExpiration() {
        Instant now = Instant.now();
        UserEntity user = userRepository.saveAndFlush(newUser(
                "constraints@example.com",
                "constraints@example.com",
                now
        ));

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO refresh_tokens (
                    id, user_id, family_id, token_hash, issued_at, expires_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')
                """,
                UUID.randomUUID(),
                user.getId(),
                UUID.randomUUID(),
                "raw-refresh-token"
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO user_action_tokens (
                    id, user_id, purpose, token_hash, expires_at, created_at
                ) VALUES (?, ?, 'PASSWORD_RESET', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                user.getId(),
                "e".repeat(64)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserEntity newUser(String email, String normalizedEmail, Instant now) {
        return new UserEntity(
                UUID.randomUUID(),
                email,
                normalizedEmail,
                PASSWORD_HASH,
                "Test Driver",
                UserStatus.PENDING_VERIFICATION,
                UserRole.USER,
                false,
                null,
                now,
                now,
                null
        );
    }

    private int insertUser(
            String email,
            String status,
            boolean emailVerified,
            Instant emailVerifiedAt,
            Instant now
    ) {
        return jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, email, normalized_email, password_hash, display_name,
                    status, role, email_verified, email_verified_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'USER', ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                email,
                email.toLowerCase(Locale.ROOT),
                PASSWORD_HASH,
                "Test Driver",
                status,
                emailVerified,
                emailVerifiedAt == null ? null : emailVerifiedAt.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
    }
}
