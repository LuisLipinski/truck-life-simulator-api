package com.luislipinski.trucklife.payroll.persistence;

import com.luislipinski.trucklife.career.domain.CareerGame;
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
@Table(name = "payslips")
public class PayslipEntity {
    @Id private UUID id;
    @Column(name = "career_id", nullable = false) private UUID careerId;
    @Enumerated(EnumType.STRING) @Column(name = "game_id", nullable = false, length = 10) private CareerGame game;
    @Column(name = "operational_week") private Integer operationalWeek;
    @Column(name = "payroll_month") private Integer payrollMonth;
    @Column(name = "start_operational_week", nullable = false) private int startOperationalWeek;
    @Column(name = "end_operational_week", nullable = false) private int endOperationalWeek;
    @Column(nullable = false) private short level;
    @Column(name = "display_currency", nullable = false, length = 3) private String displayCurrency;
    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2) private BigDecimal grossAmount;
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2) private BigDecimal taxAmount;
    @Column(name = "benefits_amount", nullable = false, precision = 14, scale = 2) private BigDecimal benefitsAmount;
    @Column(name = "per_diem_amount", nullable = false, precision = 14, scale = 2) private BigDecimal perDiemAmount;
    @Column(name = "net_salary_amount", nullable = false, precision = 14, scale = 2) private BigDecimal netSalaryAmount;
    @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 2) private BigDecimal depositAmount;
    @Column(name = "total_distance", nullable = false, precision = 14, scale = 2) private BigDecimal totalDistance;
    @Column(name = "elapsed_minutes", nullable = false) private int elapsedMinutes;
    @Column(name = "break_minutes", nullable = false) private int breakMinutes;
    @Column(name = "worked_minutes", nullable = false) private int workedMinutes;
    @Column(name = "overrun_minutes", nullable = false) private int overrunMinutes;
    @Column(name = "context_snapshot_json", nullable = false, columnDefinition = "text") private String contextSnapshotJson;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    protected PayslipEntity() {}
    public PayslipEntity(UUID id, UUID careerId, CareerGame game, Integer operationalWeek, Integer payrollMonth,
                         int startOperationalWeek, int endOperationalWeek, short level, String displayCurrency,
                         BigDecimal grossAmount, BigDecimal taxAmount, BigDecimal benefitsAmount, BigDecimal perDiemAmount,
                         BigDecimal netSalaryAmount, BigDecimal depositAmount, BigDecimal totalDistance, int elapsedMinutes,
                         int breakMinutes, int workedMinutes, int overrunMinutes, String contextSnapshotJson, Instant generatedAt) {
        this.id=id; this.careerId=careerId; this.game=game; this.operationalWeek=operationalWeek; this.payrollMonth=payrollMonth;
        this.startOperationalWeek=startOperationalWeek; this.endOperationalWeek=endOperationalWeek; this.level=level;
        this.displayCurrency=displayCurrency; this.grossAmount=grossAmount; this.taxAmount=taxAmount;
        this.benefitsAmount=benefitsAmount; this.perDiemAmount=perDiemAmount; this.netSalaryAmount=netSalaryAmount;
        this.depositAmount=depositAmount; this.totalDistance=totalDistance; this.elapsedMinutes=elapsedMinutes;
        this.breakMinutes=breakMinutes; this.workedMinutes=workedMinutes; this.overrunMinutes=overrunMinutes;
        this.contextSnapshotJson=contextSnapshotJson; this.generatedAt=generatedAt;
    }
    public UUID getId(){return id;} public UUID getCareerId(){return careerId;} public CareerGame getGame(){return game;}
    public Integer getOperationalWeek(){return operationalWeek;} public Integer getPayrollMonth(){return payrollMonth;}
    public int getStartOperationalWeek(){return startOperationalWeek;} public int getEndOperationalWeek(){return endOperationalWeek;}
    public short getLevel(){return level;} public String getDisplayCurrency(){return displayCurrency;}
    public BigDecimal getGrossAmount(){return grossAmount;} public BigDecimal getTaxAmount(){return taxAmount;}
    public BigDecimal getBenefitsAmount(){return benefitsAmount;} public BigDecimal getPerDiemAmount(){return perDiemAmount;}
    public BigDecimal getNetSalaryAmount(){return netSalaryAmount;} public BigDecimal getDepositAmount(){return depositAmount;}
    public BigDecimal getTotalDistance(){return totalDistance;} public int getElapsedMinutes(){return elapsedMinutes;}
    public int getBreakMinutes(){return breakMinutes;} public int getWorkedMinutes(){return workedMinutes;}
    public int getOverrunMinutes(){return overrunMinutes;} public String getContextSnapshotJson(){return contextSnapshotJson;}
    public Instant getGeneratedAt(){return generatedAt;}
}
