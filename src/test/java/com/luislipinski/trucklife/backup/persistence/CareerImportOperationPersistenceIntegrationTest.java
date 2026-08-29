package com.luislipinski.trucklife.backup.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CareerImportOperationPersistenceIntegrationTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired CareerImportOperationRepository importRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void clean() {
        importRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void storesAndFindsImportIdentityScopedToOwner() {
        UserEntity firstOwner = saveUser("p4-first-owner@example.com");
        UserEntity secondOwner = saveUser("p4-second-owner@example.com");
        UUID sharedOperationId = UUID.randomUUID();
        Instant now = Instant.now();

        CareerImportOperationEntity first = importRepository.saveAndFlush(new CareerImportOperationEntity(
                UUID.randomUUID(), firstOwner.getId(), sharedOperationId, "local-career-1",
                CareerGame.ATS, 12, HASH_A, now
        ));
        CareerImportOperationEntity second = importRepository.saveAndFlush(new CareerImportOperationEntity(
                UUID.randomUUID(), secondOwner.getId(), sharedOperationId, "local-career-1",
                CareerGame.ATS, 12, HASH_A, now
        ));

        assertThat(importRepository.findByUserIdAndOperationId(firstOwner.getId(), sharedOperationId))
                .contains(first);
        assertThat(importRepository.findByUserIdAndOperationId(secondOwner.getId(), sharedOperationId))
                .contains(second);
        assertThat(importRepository.findByUserIdAndGameAndSourceCareerId(
                firstOwner.getId(), CareerGame.ATS, "local-career-1"
        )).contains(first);
        assertThat(importRepository.findByIdAndUserId(first.getId(), secondOwner.getId())).isEmpty();
    }

    @Test
    void rejectsReusingOperationIdForAnotherImportOfTheSameOwner() {
        UserEntity owner = saveUser("p4-operation-owner@example.com");
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();
        importRepository.saveAndFlush(new CareerImportOperationEntity(
                UUID.randomUUID(), owner.getId(), operationId, "local-career-a",
                CareerGame.ATS, 12, HASH_A, now
        ));

        assertThatThrownBy(() -> importRepository.saveAndFlush(new CareerImportOperationEntity(
                UUID.randomUUID(), owner.getId(), operationId, "local-career-b",
                CareerGame.ATS, 12, HASH_B, now
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPreparingTheSameLocalCareerTwiceForTheSameOwnerAndGame() {
        UserEntity owner = saveUser("p4-source-owner@example.com");
        Instant now = Instant.now();
        importRepository.saveAndFlush(new CareerImportOperationEntity(
                UUID.randomUUID(), owner.getId(), UUID.randomUUID(), "local-career-a",
                CareerGame.ETS2, 12, HASH_A, now
        ));

        assertThatThrownBy(() -> importRepository.saveAndFlush(new CareerImportOperationEntity(
                UUID.randomUUID(), owner.getId(), UUID.randomUUID(), "local-career-a",
                CareerGame.ETS2, 12, HASH_B, now
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserEntity saveUser(String email) {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(), email, email.toLowerCase(), "encoded-password-not-used", email,
                UserStatus.ACTIVE, UserRole.USER, true, now, now, now, null
        ));
    }
}
