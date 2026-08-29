package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.application.FinancingOperations;
import com.luislipinski.trucklife.finance.domain.FinancialAmortizationMethod;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancingOfferResponse(FinancialProductType productType,String policyVersion,String policySource,LocalDate policyReferenceAsOf,
        String rateBasis,String jurisdictionCountryCode,String jurisdictionStateCode,String jurisdictionCity,String displayCurrency,
        BigDecimal requestedAmount,BigDecimal principal,BigDecimal downPayment,BigDecimal annualInterestRate,
        FinancialAmortizationMethod amortizationMethod,FinancialPaymentFrequency paymentFrequency,int termPeriods,
        BigDecimal installmentAmount,BigDecimal expectedTotalCost){
    static FinancingOfferResponse from(FinancingOperations.Offer o){return new FinancingOfferResponse(o.productType(),o.policyVersion(),o.policySource(),o.policyReferenceAsOf(),o.rateBasis(),o.jurisdictionCountryCode(),o.jurisdictionStateCode(),o.jurisdictionCity(),o.displayCurrency(),o.requestedAmount(),o.principal(),o.downPayment(),o.annualInterestRate(),o.amortizationMethod(),o.paymentFrequency(),o.termPeriods(),o.installmentAmount(),o.expectedTotalCost());}
}
