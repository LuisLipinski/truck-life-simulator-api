package com.luislipinski.trucklife.finance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="monthly_expense_applications")
public class MonthlyExpenseApplicationEntity {
    @Id private UUID id;
    @Column(name="career_id", nullable=false) private UUID careerId;
    @Column(name="operational_week", nullable=false) private int operationalWeek;
    @Column(name="payroll_month") private Integer payrollMonth;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal amount;
    @Column(name="display_currency", nullable=false, length=3) private String displayCurrency;
    @Column(name="context_snapshot_json", nullable=false, columnDefinition="text") private String contextSnapshotJson;
    @Column(name="applied_at", nullable=false) private Instant appliedAt;

    protected MonthlyExpenseApplicationEntity() {}
    public MonthlyExpenseApplicationEntity(UUID id, UUID careerId, int operationalWeek, Integer payrollMonth,
                                            BigDecimal amount, String displayCurrency, String contextSnapshotJson, Instant appliedAt) {
        this.id=id; this.careerId=careerId; this.operationalWeek=operationalWeek; this.payrollMonth=payrollMonth;
        this.amount=amount; this.displayCurrency=displayCurrency; this.contextSnapshotJson=contextSnapshotJson; this.appliedAt=appliedAt;
    }
    public UUID getId(){return id;} public UUID getCareerId(){return careerId;} public BigDecimal getAmount(){return amount;}
}
