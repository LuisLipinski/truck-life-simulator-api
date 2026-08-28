package com.luislipinski.trucklife.finance.persistence;

import com.luislipinski.trucklife.finance.domain.EmergencyReserveEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="emergency_reserve_events")
public class EmergencyReserveEventEntity {
    @Id private UUID id;
    @Column(name="career_id", nullable=false) private UUID careerId;
    @Column(name="payslip_id") private UUID payslipId;
    @Enumerated(EnumType.STRING) @Column(name="event_type", nullable=false, length=30) private EmergencyReserveEventType eventType;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal amount;
    @Column(name="balance_before", nullable=false, precision=14, scale=2) private BigDecimal balanceBefore;
    @Column(name="balance_after", nullable=false, precision=14, scale=2) private BigDecimal balanceAfter;
    @Column(name="display_currency", nullable=false, length=3) private String displayCurrency;
    @Column(name="operational_week", nullable=false) private int operationalWeek;
    @Column(name="payroll_month") private Integer payrollMonth;
    @Column(length=240) private String reason;
    @Column(name="recorded_at", nullable=false) private Instant recordedAt;

    protected EmergencyReserveEventEntity() {}
    public EmergencyReserveEventEntity(UUID id, UUID careerId, UUID payslipId, EmergencyReserveEventType eventType,
                                       BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, String displayCurrency,
                                       int operationalWeek, Integer payrollMonth, String reason, Instant recordedAt) {
        this.id=id; this.careerId=careerId; this.payslipId=payslipId; this.eventType=eventType; this.amount=amount;
        this.balanceBefore=balanceBefore; this.balanceAfter=balanceAfter; this.displayCurrency=displayCurrency;
        this.operationalWeek=operationalWeek; this.payrollMonth=payrollMonth; this.reason=reason; this.recordedAt=recordedAt;
    }
    public UUID getId(){return id;} public UUID getCareerId(){return careerId;}
}
