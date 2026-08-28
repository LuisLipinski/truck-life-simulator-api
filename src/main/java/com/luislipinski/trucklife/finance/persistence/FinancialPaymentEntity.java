package com.luislipinski.trucklife.finance.persistence;

import com.luislipinski.trucklife.finance.domain.FinancialPaymentType;
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
@Table(name="financial_payments")
public class FinancialPaymentEntity {
    @Id private UUID id;
    @Column(name="contract_id",nullable=false) private UUID contractId;
    @Column(name="operation_id",nullable=false,unique=true) private UUID operationId;
    @Enumerated(EnumType.STRING) @Column(name="payment_type",nullable=false,length=30) private FinancialPaymentType paymentType;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal amount;
    @Column(name="principal_amount",nullable=false,precision=14,scale=2) private BigDecimal principalAmount;
    @Column(name="interest_amount",nullable=false,precision=14,scale=2) private BigDecimal interestAmount;
    @Column(name="fee_amount",nullable=false,precision=14,scale=2) private BigDecimal feeAmount;
    @Column(name="balance_before",nullable=false,precision=14,scale=2) private BigDecimal balanceBefore;
    @Column(name="balance_after",nullable=false,precision=14,scale=2) private BigDecimal balanceAfter;
    @Column(name="operational_week",nullable=false) private int operationalWeek;
    @Column(name="payroll_month") private Integer payrollMonth;
    @Column(name="display_currency",nullable=false,length=3) private String displayCurrency;
    @Column(name="metadata_json",nullable=false,columnDefinition="text") private String metadataJson;
    @Column(name="recorded_at",nullable=false) private Instant recordedAt;

    protected FinancialPaymentEntity() {}
    public FinancialPaymentEntity(UUID id,UUID contractId,UUID operationId,FinancialPaymentType paymentType,BigDecimal amount,
            BigDecimal principalAmount,BigDecimal interestAmount,BigDecimal feeAmount,BigDecimal balanceBefore,BigDecimal balanceAfter,
            int operationalWeek,Integer payrollMonth,String displayCurrency,String metadataJson,Instant recordedAt){this.id=id;this.contractId=contractId;
        this.operationId=operationId;this.paymentType=paymentType;this.amount=amount;this.principalAmount=principalAmount;this.interestAmount=interestAmount;
        this.feeAmount=feeAmount;this.balanceBefore=balanceBefore;this.balanceAfter=balanceAfter;this.operationalWeek=operationalWeek;
        this.payrollMonth=payrollMonth;this.displayCurrency=displayCurrency;this.metadataJson=metadataJson;this.recordedAt=recordedAt;}
    public UUID getId(){return id;} public UUID getContractId(){return contractId;} public UUID getOperationId(){return operationId;}
    public FinancialPaymentType getPaymentType(){return paymentType;} public BigDecimal getAmount(){return amount;}
    public BigDecimal getPrincipalAmount(){return principalAmount;} public BigDecimal getInterestAmount(){return interestAmount;}
    public BigDecimal getFeeAmount(){return feeAmount;} public BigDecimal getBalanceBefore(){return balanceBefore;} public BigDecimal getBalanceAfter(){return balanceAfter;}
    public int getOperationalWeek(){return operationalWeek;} public Integer getPayrollMonth(){return payrollMonth;} public String getDisplayCurrency(){return displayCurrency;}
    public String getMetadataJson(){return metadataJson;} public Instant getRecordedAt(){return recordedAt;}
}
