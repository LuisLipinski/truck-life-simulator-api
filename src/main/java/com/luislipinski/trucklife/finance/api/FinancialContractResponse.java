package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.application.FinancingOperations;
import com.luislipinski.trucklife.finance.domain.FinancialAmortizationMethod;
import com.luislipinski.trucklife.finance.domain.FinancialContractStatus;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinancialContractResponse(UUID id,FinancialProductType productType,FinancialContractStatus status,String policyVersion,String policySource,
        LocalDate policyReferenceAsOf,String rateBasis,String jurisdictionCountryCode,String jurisdictionStateCode,String jurisdictionCity,
        String displayCurrency,BigDecimal requestedAmount,BigDecimal principal,BigDecimal downPayment,BigDecimal annualInterestRate,
        FinancialAmortizationMethod amortizationMethod,FinancialPaymentFrequency paymentFrequency,int termPeriods,int currentScheduleVersion,
        BigDecimal expectedTotalCost,BigDecimal remainingPrincipal,int originatedOperationalWeek,Integer originatedPayrollMonth,
        Instant createdAt,List<FinancialInstallmentResponse> installments,List<FinancialPaymentResponse> payments,List<FinancialContractEventResponse> events){
    static FinancialContractResponse from(FinancingOperations.ContractDetails d){FinancialContractEntity c=d.contract();return new FinancialContractResponse(c.getId(),c.getProductType(),c.getStatus(),c.getPolicyVersion(),c.getPolicySource(),c.getPolicyReferenceAsOf(),c.getRateBasis(),c.getJurisdictionCountryCode(),c.getJurisdictionStateCode(),c.getJurisdictionCity(),c.getDisplayCurrency(),c.getRequestedAmount(),c.getPrincipal(),c.getDownPayment(),c.getAnnualInterestRate(),c.getAmortizationMethod(),c.getPaymentFrequency(),c.getTermPeriods(),c.getCurrentScheduleVersion(),c.getExpectedTotalCost(),c.getRemainingPrincipal(),c.getOriginatedOperationalWeek(),c.getOriginatedPayrollMonth(),c.getCreatedAt(),d.installments().stream().map(FinancialInstallmentResponse::from).toList(),d.payments().stream().map(FinancialPaymentResponse::from).toList(),d.events().stream().map(FinancialContractEventResponse::from).toList());}
}
