package com.luislipinski.trucklife.incident.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_payslip_deductions")
public class IncidentPayslipDeductionEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "payslip_id", nullable = false)
    private UUID payslipId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected IncidentPayslipDeductionEntity() {
    }

    public IncidentPayslipDeductionEntity(
            UUID id,
            UUID incidentId,
            UUID payslipId,
            BigDecimal amount,
            Instant recordedAt
    ) {
        this.id = id;
        this.incidentId = incidentId;
        this.payslipId = payslipId;
        this.amount = amount;
        this.recordedAt = recordedAt;
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public UUID getPayslipId() { return payslipId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getRecordedAt() { return recordedAt; }
}
