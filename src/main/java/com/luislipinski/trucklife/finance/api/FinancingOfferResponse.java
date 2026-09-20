package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.application.FinancingOperations;
import com.luislipinski.trucklife.finance.domain.FinancialAmortizationMethod;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancingOfferResponse(
        FinancialProductType productType,
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
        BigDecimal installmentAmount,
        BigDecimal expectedTotalCost,
        String jurisdictionRuleSource,
        String jurisdictionRuleSummary,
        BigDecimal legalAprCap,
        String prepaymentRuleSummary,
        String latePaymentRuleSummary,
        BigDecimal prepaymentFeeRate,
        BigDecimal lateFeeRate,
        int maxMissedInstallments,
        BigDecimal downPaymentRate
) {
    static FinancingOfferResponse from(FinancingOperations.Offer offer) {
        return new FinancingOfferResponse(
                offer.productType(),
                offer.policyVersion(),
                offer.policySource(),
                offer.policyReferenceAsOf(),
                offer.rateBasis(),
                offer.jurisdictionCountryCode(),
                offer.jurisdictionStateCode(),
                offer.jurisdictionCity(),
                offer.displayCurrency(),
                offer.requestedAmount(),
                offer.principal(),
                offer.downPayment(),
                offer.annualInterestRate(),
                offer.amortizationMethod(),
                offer.paymentFrequency(),
                offer.termPeriods(),
                offer.installmentAmount(),
                offer.expectedTotalCost(),
                offer.jurisdictionRuleSource(),
                offer.jurisdictionRuleSummary(),
                offer.legalAprCap(),
                offer.prepaymentRuleSummary(),
                offer.latePaymentRuleSummary(),
                offer.prepaymentFeeRate(),
                offer.lateFeeRate(),
                offer.maxMissedInstallments(),
                offer.downPaymentRate()
        );
    }
}
