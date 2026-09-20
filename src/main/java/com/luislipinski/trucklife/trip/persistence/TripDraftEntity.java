package com.luislipinski.trucklife.trip.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_drafts")
public class TripDraftEntity {

    @Id
    @Column(name = "career_id", nullable = false)
    private UUID careerId;

    @Column(name = "operational_week", nullable = false)
    private int operationalWeek;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripDraftEntity() {}

    public TripDraftEntity(UUID careerId, int operationalWeek, String payloadJson, Instant updatedAt) {
        this.careerId = careerId;
        this.operationalWeek = operationalWeek;
        this.payloadJson = payloadJson;
        this.updatedAt = updatedAt;
    }

    public UUID getCareerId() {
        return careerId;
    }

    public int getOperationalWeek() {
        return operationalWeek;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(int operationalWeek, String payloadJson, Instant updatedAt) {
        this.operationalWeek = operationalWeek;
        this.payloadJson = payloadJson;
        this.updatedAt = updatedAt;
    }
}
