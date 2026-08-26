package com.luislipinski.trucklife.career.persistence;

import com.luislipinski.trucklife.career.domain.CareerEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "career_events")
public class CareerEventEntity {

    @Id
    private UUID id;

    @Column(name = "career_id", nullable = false)
    private UUID careerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private CareerEventType type;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "changes_json", nullable = false, columnDefinition = "text")
    private String changesJson;

    protected CareerEventEntity() {
    }

    public CareerEventEntity(
            UUID id,
            UUID careerId,
            CareerEventType type,
            LocalDate effectiveDate,
            Instant recordedAt,
            String changesJson
    ) {
        this.id = id;
        this.careerId = careerId;
        this.type = type;
        this.effectiveDate = effectiveDate;
        this.recordedAt = recordedAt;
        this.changesJson = changesJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCareerId() {
        return careerId;
    }

    public CareerEventType getType() {
        return type;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getChangesJson() {
        return changesJson;
    }
}
