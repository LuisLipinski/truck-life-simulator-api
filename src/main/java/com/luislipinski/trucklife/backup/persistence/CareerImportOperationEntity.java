package com.luislipinski.trucklife.backup.persistence;

import com.luislipinski.trucklife.backup.domain.CareerImportStatus;
import com.luislipinski.trucklife.career.domain.CareerGame;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "career_import_operations")
public class CareerImportOperationEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "source_career_id", nullable = false, length = 200)
    private String sourceCareerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_id", nullable = false, length = 10)
    private CareerGame game;

    @Column(name = "source_version", nullable = false)
    private int sourceVersion;

    @Column(name = "snapshot_sha256", nullable = false, length = 64)
    private String snapshotSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CareerImportStatus status;

    @Column(name = "imported_career_id")
    private UUID importedCareerId;

    @Column(name = "result_summary_json", columnDefinition = "text")
    private String resultSummaryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerImportOperationEntity() {
    }

    public CareerImportOperationEntity(
            UUID id,
            UUID userId,
            UUID operationId,
            String sourceCareerId,
            CareerGame game,
            int sourceVersion,
            String snapshotSha256,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.operationId = Objects.requireNonNull(operationId);
        this.sourceCareerId = requireText(sourceCareerId, "sourceCareerId");
        this.game = Objects.requireNonNull(game);
        if (sourceVersion != 12) {
            throw new IllegalArgumentException("Only local snapshot version 12 is supported");
        }
        this.sourceVersion = sourceVersion;
        this.snapshotSha256 = requireHash(snapshotSha256);
        this.status = CareerImportStatus.PROCESSING;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public void complete(UUID careerId, String summaryJson, Instant now) {
        if (status == CareerImportStatus.COMPLETED) {
            if (!Objects.equals(importedCareerId, careerId) || !Objects.equals(resultSummaryJson, summaryJson)) {
                throw new IllegalStateException("Completed import cannot be rewritten");
            }
            return;
        }
        importedCareerId = Objects.requireNonNull(careerId);
        resultSummaryJson = requireText(summaryJson, "summaryJson");
        status = CareerImportStatus.COMPLETED;
        updatedAt = Objects.requireNonNull(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public String getSourceCareerId() {
        return sourceCareerId;
    }

    public CareerGame getGame() {
        return game;
    }

    public int getSourceVersion() {
        return sourceVersion;
    }

    public String getSnapshotSha256() {
        return snapshotSha256;
    }

    public CareerImportStatus getStatus() {
        return status;
    }

    public UUID getImportedCareerId() {
        return importedCareerId;
    }

    public String getResultSummaryJson() {
        return resultSummaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.strip();
    }

    private static String requireHash(String value) {
        String hash = requireText(value, "snapshotSha256");
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("snapshotSha256 must be a lowercase SHA-256 value");
        }
        return hash;
    }
}
