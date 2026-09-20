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

public record FinancialContractResponse(
        UUID id,
        FinancialProductType productType,
        FinancialContractStatus status,
        String policyVersion,
        String policySource,
        LocalDate policyReferenceAsOf,
        String rateBasis,
        String jurisdictionCountryCode,
        String jurisdictionStateCode,
        String jurisdictionCity,
        String displayCurrency,
        BigDecimal requestedAmount,
        BigDecimal principal,
        BigDecimal downPayment,
        BigDecimal annualInterestRate,
        FinancialAmortizationMethod amortizationMethod,
        FinancialPaymentFrequency paymentFrequency,
        int termPeriods,
        int currentScheduleVersion,
        BigDecimal expectedTotalCost,
        BigDecimal remainingPrincipal,
        BigDecimal prepaymentFeeRate,
        BigDecimal lateFeeRate,
        int maxMissedInstallments,
        int originatedOperationalWeek,
        Integer originatedPayrollMonth,
        Instant createdAt,
        List<FinancialInstallmentResponse> installments,
        List<FinancialPaymentResponse> payments,
        List<FinancialContractEventResponse> events
) {
    static FinancialContractResponse from(FinancingOperations.ContractDetails details) {
        FinancialContractEntity contract=details.contract();
        return new FinancialContractResponse(
                contract.getId(),
                contract.getProductType(),
                contract.getStatus(),
                contract.getPolicyVersion(),
                contract.getPolicySource(),
                contract.getPolicyReferenceAsOf(),
                contract.getRateBasis(),
                contract.getJurisdictionCountryCode(),
                contract.getJurisdictionStateCode(),
                contract.getJurisdictionCity(),
                contract.getDisplayCurrency(),
                contract.getRequestedAmount(),
                contract.getPrincipal(),
                contract.getDownPayment(),
                contract.getAnnualInterestRate(),
                contract.getAmortizationMethod(),
                contract.getPaymentFrequency(),
                contract.getTermPeriods(),
                contract.getCurrentScheduleVersion(),
                contract.getExpectedTotalCost(),
                contract.getRemainingPrincipal(),
                contract.getPrepaymentFeeRate(),
                contract.getLateFeeRate(),
                contract.getMaxMissedInstallments(),
                contract.getOriginatedOperationalWeek(),
                contract.getOriginatedPayrollMonth(),
                contract.getCreatedAt(),
                details.installments().stream().map(FinancialInstallmentResponse::from).toList(),
                details.payments().stream().map(FinancialPaymentResponse::from).toList(),
                details.events().stream().map(FinancialContractEventResponse::from).toList()
        );
    }
}
