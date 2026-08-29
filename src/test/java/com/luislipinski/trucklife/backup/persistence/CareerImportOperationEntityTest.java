package com.luislipinski.trucklife.backup.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.backup.domain.CareerImportStatus;
import com.luislipinski.trucklife.career.domain.CareerGame;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CareerImportOperationEntityTest {

    @Test
    void startsProcessingAndCanOnlyCompleteImmutably() {
        Instant createdAt = Instant.parse("2026-08-29T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID careerId = UUID.randomUUID();
        CareerImportOperationEntity operation = new CareerImportOperationEntity(
                UUID.randomUUID(), userId, operationId, "local-career-1",
                CareerGame.ATS, 12, "a".repeat(64), createdAt
        );

        assertThat(operation.getUserId()).isEqualTo(userId);
        assertThat(operation.getOperationId()).isEqualTo(operationId);
        assertThat(operation.getSourceCareerId()).isEqualTo("local-career-1");
        assertThat(operation.getGame()).isEqualTo(CareerGame.ATS);
        assertThat(operation.getSourceVersion()).isEqualTo(12);
        assertThat(operation.getSnapshotSha256()).isEqualTo("a".repeat(64));
        assertThat(operation.getStatus()).isEqualTo(CareerImportStatus.PROCESSING);
        assertThat(operation.getImportedCareerId()).isNull();
        assertThat(operation.getResultSummaryJson()).isNull();
        assertThat(operation.getCreatedAt()).isEqualTo(createdAt);

        Instant completedAt = createdAt.plusSeconds(10);
        operation.complete(careerId, "{\"trips\":2}", completedAt);
        operation.complete(careerId, "{\"trips\":2}", completedAt.plusSeconds(10));

        assertThat(operation.getStatus()).isEqualTo(CareerImportStatus.COMPLETED);
        assertThat(operation.getImportedCareerId()).isEqualTo(careerId);
        assertThat(operation.getResultSummaryJson()).isEqualTo("{\"trips\":2}");
        assertThat(operation.getUpdatedAt()).isEqualTo(completedAt);
        assertThatThrownBy(() -> operation.complete(UUID.randomUUID(), "{\"trips\":3}", completedAt))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsupportedVersionOrMalformedHash() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new CareerImportOperationEntity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "local-career",
                CareerGame.ATS, 11, "a".repeat(64), now
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CareerImportOperationEntity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "local-career",
                CareerGame.ATS, 12, "NOT-A-HASH", now
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
