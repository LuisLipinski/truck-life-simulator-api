package com.luislipinski.trucklife.finance.persistence;

import com.luislipinski.trucklife.finance.domain.FinancialAmortizationMethod;
import com.luislipinski.trucklife.finance.domain.FinancialContractStatus;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="financial_contracts")
public class FinancialContractEntity {
    @Id private UUID id;
    @Column(name="career_id",nullable=false) private UUID careerId;
    @Column(name="origination_operation_id",nullable=false,unique=true) private UUID originationOperationId;
    @Enumerated(EnumType.STRING) @Column(name="product_type",nullable=false,length=30) private FinancialProductType productType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private FinancialContractStatus status;
    @Column(name="policy_version",nullable=false,length=100) private String policyVersion;
    @Column(name="policy_source",nullable=false,length=500) private String policySource;
    @Column(name="policy_reference_as_of",nullable=false) private LocalDate policyReferenceAsOf;
    @Column(name="rate_basis",nullable=false,length=80) private String rateBasis;
    @Column(name="jurisdiction_country_code",length=10) private String jurisdictionCountryCode;
    @Column(name="jurisdiction_state_code",length=10) private String jurisdictionStateCode;
    @Column(name="jurisdiction_city",nullable=false,length=160) private String jurisdictionCity;
    @Column(name="display_currency",nullable=false,length=3) private String displayCurrency;
    @Column(name="requested_amount",nullable=false,precision=14,scale=2) private BigDecimal requestedAmount;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal principal;
    @Column(name="down_payment",nullable=false,precision=14,scale=2) private BigDecimal downPayment;
    @Column(name="annual_interest_rate",nullable=false,precision=12,scale=10) private BigDecimal annualInterestRate;
    @Enumerated(EnumType.STRING) @Column(name="amortization_method",nullable=false,length=50) private FinancialAmortizationMethod amortizationMethod;
    @Enumerated(EnumType.STRING) @Column(name="payment_frequency",nullable=false,length=20) private FinancialPaymentFrequency paymentFrequency;
    @Column(name="term_periods",nullable=false) private int termPeriods;
    @Column(name="current_schedule_version",nullable=false) private int currentScheduleVersion;
    @Column(name="expected_total_cost",nullable=false,precision=14,scale=2) private BigDecimal expectedTotalCost;
    @Column(name="remaining_principal",nullable=false,precision=14,scale=2) private BigDecimal remainingPrincipal;
    @Column(name="prepayment_fee_rate",nullable=false,precision=12,scale=10) private BigDecimal prepaymentFeeRate;
    @Column(name="late_fee_rate",nullable=false,precision=12,scale=10) private BigDecimal lateFeeRate;
    @Column(name="max_missed_installments",nullable=false) private int maxMissedInstallments;
    @Column(name="originated_operational_week",nullable=false) private int originatedOperationalWeek;
    @Column(name="originated_payroll_month") private Integer originatedPayrollMonth;
    @Column(name="policy_snapshot_json",nullable=false,columnDefinition="text") private String policySnapshotJson;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;

    protected FinancialContractEntity() {}

    public FinancialContractEntity(UUID id,UUID careerId,UUID originationOperationId,FinancialProductType productType,
            FinancialContractStatus status,String policyVersion,String policySource,LocalDate policyReferenceAsOf,String rateBasis,
            String jurisdictionCountryCode,String jurisdictionStateCode,String jurisdictionCity,String displayCurrency,
            BigDecimal requestedAmount,BigDecimal principal,BigDecimal downPayment,BigDecimal annualInterestRate,
            FinancialAmortizationMethod amortizationMethod,FinancialPaymentFrequency paymentFrequency,int termPeriods,
            int currentScheduleVersion,BigDecimal expectedTotalCost,BigDecimal remainingPrincipal,BigDecimal prepaymentFeeRate,
            BigDecimal lateFeeRate,int maxMissedInstallments,int originatedOperationalWeek,Integer originatedPayrollMonth,
            String policySnapshotJson,Instant createdAt,Instant updatedAt) {
        this.id=id;this.careerId=careerId;this.originationOperationId=originationOperationId;this.productType=productType;this.status=status;
        this.policyVersion=policyVersion;this.policySource=policySource;this.policyReferenceAsOf=policyReferenceAsOf;this.rateBasis=rateBasis;
        this.jurisdictionCountryCode=jurisdictionCountryCode;this.jurisdictionStateCode=jurisdictionStateCode;this.jurisdictionCity=jurisdictionCity;
        this.displayCurrency=displayCurrency;this.requestedAmount=requestedAmount;this.principal=principal;this.downPayment=downPayment;
        this.annualInterestRate=annualInterestRate;this.amortizationMethod=amortizationMethod;this.paymentFrequency=paymentFrequency;
        this.termPeriods=termPeriods;this.currentScheduleVersion=currentScheduleVersion;this.expectedTotalCost=expectedTotalCost;
        this.remainingPrincipal=remainingPrincipal;this.prepaymentFeeRate=prepaymentFeeRate;this.lateFeeRate=lateFeeRate;
        this.maxMissedInstallments=maxMissedInstallments;this.originatedOperationalWeek=originatedOperationalWeek;
        this.originatedPayrollMonth=originatedPayrollMonth;this.policySnapshotJson=policySnapshotJson;this.createdAt=createdAt;this.updatedAt=updatedAt;
    }

    public UUID getId(){return id;} public UUID getCareerId(){return careerId;} public UUID getOriginationOperationId(){return originationOperationId;}
    public FinancialProductType getProductType(){return productType;} public FinancialContractStatus getStatus(){return status;}
    public String getPolicyVersion(){return policyVersion;} public String getPolicySource(){return policySource;}
    public LocalDate getPolicyReferenceAsOf(){return policyReferenceAsOf;} public String getRateBasis(){return rateBasis;}
    public String getJurisdictionCountryCode(){return jurisdictionCountryCode;} public String getJurisdictionStateCode(){return jurisdictionStateCode;}
    public String getJurisdictionCity(){return jurisdictionCity;} public String getDisplayCurrency(){return displayCurrency;}
    public BigDecimal getRequestedAmount(){return requestedAmount;} public BigDecimal getPrincipal(){return principal;}
    public BigDecimal getDownPayment(){return downPayment;} public BigDecimal getAnnualInterestRate(){return annualInterestRate;}
    public FinancialAmortizationMethod getAmortizationMethod(){return amortizationMethod;} public FinancialPaymentFrequency getPaymentFrequency(){return paymentFrequency;}
    public int getTermPeriods(){return termPeriods;} public int getCurrentScheduleVersion(){return currentScheduleVersion;}
    public BigDecimal getExpectedTotalCost(){return expectedTotalCost;} public BigDecimal getRemainingPrincipal(){return remainingPrincipal;}
    public BigDecimal getPrepaymentFeeRate(){return prepaymentFeeRate;} public BigDecimal getLateFeeRate(){return lateFeeRate;}
    public int getMaxMissedInstallments(){return maxMissedInstallments;} public int getOriginatedOperationalWeek(){return originatedOperationalWeek;}
    public Integer getOriginatedPayrollMonth(){return originatedPayrollMonth;} public String getPolicySnapshotJson(){return policySnapshotJson;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}

    public void applyPrincipalPayment(BigDecimal amount,Instant now){
        if(amount==null||amount.signum()<0||amount.compareTo(remainingPrincipal)>0)throw new IllegalArgumentException("Principal payment is invalid");
        remainingPrincipal=remainingPrincipal.subtract(amount);updatedAt=now;
    }
    public int advanceScheduleVersion(Instant now){currentScheduleVersion+=1;updatedAt=now;return currentScheduleVersion;}
    public void markActive(Instant now){if(status!=FinancialContractStatus.PAID_OFF&&status!=FinancialContractStatus.DEFAULTED){status=FinancialContractStatus.ACTIVE;updatedAt=now;}}
    public void markDelinquent(Instant now){if(status==FinancialContractStatus.ACTIVE){status=FinancialContractStatus.DELINQUENT;updatedAt=now;}}
    public void markDefaulted(Instant now){if(status!=FinancialContractStatus.PAID_OFF){status=FinancialContractStatus.DEFAULTED;updatedAt=now;}}
    public void markPaidOff(Instant now){remainingPrincipal=BigDecimal.ZERO.setScale(2);status=FinancialContractStatus.PAID_OFF;updatedAt=now;}
}
