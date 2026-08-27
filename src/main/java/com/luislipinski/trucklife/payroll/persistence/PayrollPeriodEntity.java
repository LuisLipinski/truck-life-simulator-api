package com.luislipinski.trucklife.payroll.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payroll_periods")
public class PayrollPeriodEntity {

    @Id
    private UUID id;

    @Column(name = "career_id", nullable = false)
    private UUID careerId;

    @Column(name = "operational_week", nullable = false)
    private int operationalWeek;

    @Column(name = "payroll_month")
    private Integer payrollMonth;

    @Column(name = "context_snapshot_json", nullable = false, columnDefinition = "text")
    private String contextSnapshotJson;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    protected PayrollPeriodEntity() {
    }

    public PayrollPeriodEntity(
            UUID id,
            UUID careerId,
            int operationalWeek,
            Integer payrollMonth,
            String contextSnapshotJson,
            Instant closedAt
    ) {
        this.id = id;
        this.careerId = careerId;
        this.operationalWeek = operationalWeek;
        this.payrollMonth = payrollMonth;
        this.contextSnapshotJson = contextSnapshotJson;
        this.closedAt = closedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCareerId() {
        return careerId;
    }

    public int getOperationalWeek() {
        return operationalWeek;
    }

    public Integer getPayrollMonth() {
        return payrollMonth;
    }

    public String getContextSnapshotJson() {
        return contextSnapshotJson;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
