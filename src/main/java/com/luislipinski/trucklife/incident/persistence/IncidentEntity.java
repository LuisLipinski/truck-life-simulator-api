package com.luislipinski.trucklife.incident.persistence;

import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.domain.IncidentStatus;
import com.luislipinski.trucklife.incident.domain.IncidentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id
    private UUID id;

    @Column(name = "career_id", nullable = false)
    private UUID careerId;

    @Column(name = "related_trip_id")
    private UUID relatedTripId;

    @Column(name = "operational_week", nullable = false)
    private int operationalWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 30)
    private IncidentType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "remaining_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "route_label", nullable = false, length = 500)
    private String routeLabel;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_method", nullable = false, length = 20)
    private IncidentChargeMethod chargeMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected IncidentEntity() {
    }

    public IncidentEntity(
            UUID id,
            UUID careerId,
            UUID relatedTripId,
            int operationalWeek,
            IncidentType type,
            BigDecimal amount,
            BigDecimal remainingAmount,
            String routeLabel,
            String description,
            IncidentChargeMethod chargeMethod,
            IncidentStatus status,
            Instant recordedAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.careerId = careerId;
        this.relatedTripId = relatedTripId;
        this.operationalWeek = operationalWeek;
        this.type = type;
        this.amount = amount;
        this.remainingAmount = remainingAmount;
        this.routeLabel = routeLabel;
        this.description = description;
        this.chargeMethod = chargeMethod;
        this.status = status;
        this.recordedAt = recordedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getCareerId() { return careerId; }
    public UUID getRelatedTripId() { return relatedTripId; }
    public int getOperationalWeek() { return operationalWeek; }
    public IncidentType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public String getRouteLabel() { return routeLabel; }
    public String getDescription() { return description; }
    public IncidentChargeMethod getChargeMethod() { return chargeMethod; }
    public IncidentStatus getStatus() { return status; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public boolean canCancel() {
        return chargeMethod == IncidentChargeMethod.PAYSLIP
                && status == IncidentStatus.PENDING_PAYSLIP
                && remainingAmount.compareTo(amount) == 0;
    }

    public void cancel(Instant now) {
        if (!canCancel()) {
            throw new IllegalStateException("Only untouched pending payslip incidents can be cancelled");
        }
        remainingAmount = BigDecimal.ZERO.setScale(2);
        status = IncidentStatus.CANCELLED;
        updatedAt = now;
    }

    public void applyPayslipDeduction(BigDecimal deduction, Instant now) {
        if (chargeMethod != IncidentChargeMethod.PAYSLIP || status == IncidentStatus.CANCELLED) {
            throw new IllegalStateException("Incident is not eligible for payslip deduction");
        }
        if (deduction == null || deduction.signum() <= 0 || deduction.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("Incident deduction must be positive and not exceed the remaining amount");
        }
        remainingAmount = remainingAmount.subtract(deduction);
        status = remainingAmount.signum() == 0
                ? IncidentStatus.DEDUCTED_PAYSLIP
                : IncidentStatus.PARTIALLY_DEDUCTED;
        updatedAt = now;
    }
}
