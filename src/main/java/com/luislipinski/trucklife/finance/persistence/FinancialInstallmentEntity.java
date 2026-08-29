package com.luislipinski.trucklife.finance.persistence;

import com.luislipinski.trucklife.finance.domain.FinancialInstallmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="financial_installments")
public class FinancialInstallmentEntity {
    @Id private UUID id;
    @Column(name="contract_id",nullable=false) private UUID contractId;
    @Column(name="schedule_version",nullable=false) private int scheduleVersion;
    @Column(name="installment_number",nullable=false) private int installmentNumber;
    @Column(name="due_operational_week") private Integer dueOperationalWeek;
    @Column(name="due_payroll_month") private Integer duePayrollMonth;
    @Column(name="scheduled_amount",nullable=false,precision=14,scale=2) private BigDecimal scheduledAmount;
    @Column(name="principal_amount",nullable=false,precision=14,scale=2) private BigDecimal principalAmount;
    @Column(name="interest_amount",nullable=false,precision=14,scale=2) private BigDecimal interestAmount;
    @Column(name="fee_amount",nullable=false,precision=14,scale=2) private BigDecimal feeAmount;
    @Column(name="paid_amount",nullable=false,precision=14,scale=2) private BigDecimal paidAmount;
    @Column(name="principal_paid",nullable=false,precision=14,scale=2) private BigDecimal principalPaid;
    @Column(name="interest_paid",nullable=false,precision=14,scale=2) private BigDecimal interestPaid;
    @Column(name="fee_paid",nullable=false,precision=14,scale=2) private BigDecimal feePaid;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private FinancialInstallmentStatus status;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    protected FinancialInstallmentEntity() {}

    public FinancialInstallmentEntity(UUID id,UUID contractId,int scheduleVersion,int installmentNumber,Integer dueOperationalWeek,
            Integer duePayrollMonth,BigDecimal scheduledAmount,BigDecimal principalAmount,BigDecimal interestAmount,BigDecimal feeAmount,
            Instant createdAt){this.id=id;this.contractId=contractId;this.scheduleVersion=scheduleVersion;this.installmentNumber=installmentNumber;
        this.dueOperationalWeek=dueOperationalWeek;this.duePayrollMonth=duePayrollMonth;this.scheduledAmount=money(scheduledAmount);
        this.principalAmount=money(principalAmount);this.interestAmount=money(interestAmount);this.feeAmount=money(feeAmount);
        this.paidAmount=zero();this.principalPaid=zero();this.interestPaid=zero();this.feePaid=zero();this.status=FinancialInstallmentStatus.SCHEDULED;
        this.createdAt=createdAt;this.updatedAt=createdAt;}

    public UUID getId(){return id;} public UUID getContractId(){return contractId;} public int getScheduleVersion(){return scheduleVersion;}
    public int getInstallmentNumber(){return installmentNumber;} public Integer getDueOperationalWeek(){return dueOperationalWeek;}
    public Integer getDuePayrollMonth(){return duePayrollMonth;} public BigDecimal getScheduledAmount(){return scheduledAmount;}
    public BigDecimal getPrincipalAmount(){return principalAmount;} public BigDecimal getInterestAmount(){return interestAmount;}
    public BigDecimal getFeeAmount(){return feeAmount;} public BigDecimal getPaidAmount(){return paidAmount;}
    public BigDecimal getPrincipalPaid(){return principalPaid;} public BigDecimal getInterestPaid(){return interestPaid;}
    public BigDecimal getFeePaid(){return feePaid;} public FinancialInstallmentStatus getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}

    public BigDecimal outstanding(){return scheduledAmount.subtract(paidAmount).setScale(2,RoundingMode.UNNECESSARY);}
    public BigDecimal outstandingPrincipal(){return principalAmount.subtract(principalPaid).setScale(2,RoundingMode.UNNECESSARY);}
    public BigDecimal outstandingInterest(){return interestAmount.subtract(interestPaid).setScale(2,RoundingMode.UNNECESSARY);}
    public BigDecimal outstandingFee(){return feeAmount.subtract(feePaid).setScale(2,RoundingMode.UNNECESSARY);}
    public boolean isOpen(){return status!=FinancialInstallmentStatus.PAID&&status!=FinancialInstallmentStatus.SUPERSEDED;}

    public Allocation applyPayment(BigDecimal requested,Instant now){
        BigDecimal amount=money(requested);if(amount.signum()<=0||amount.compareTo(outstanding())>0)throw new IllegalArgumentException("Installment payment is invalid");
        BigDecimal remaining=amount;BigDecimal fee=remaining.min(outstandingFee());feePaid=feePaid.add(fee);remaining=remaining.subtract(fee);
        BigDecimal interest=remaining.min(outstandingInterest());interestPaid=interestPaid.add(interest);remaining=remaining.subtract(interest);
        BigDecimal principal=remaining.min(outstandingPrincipal());principalPaid=principalPaid.add(principal);remaining=remaining.subtract(principal);
        if(remaining.signum()!=0)throw new IllegalStateException("Installment allocation did not reconcile");
        paidAmount=paidAmount.add(amount);status=paidAmount.compareTo(scheduledAmount)==0?FinancialInstallmentStatus.PAID:FinancialInstallmentStatus.PARTIALLY_PAID;updatedAt=now;
        return new Allocation(principal,interest,fee);
    }
    public void markOverdue(Instant now){if(isOpen()){status=FinancialInstallmentStatus.OVERDUE;updatedAt=now;}}
    public void supersede(Instant now){if(isOpen()){status=FinancialInstallmentStatus.SUPERSEDED;updatedAt=now;}}
    private BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.UNNECESSARY);}private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    public record Allocation(BigDecimal principal,BigDecimal interest,BigDecimal fee){}
}
