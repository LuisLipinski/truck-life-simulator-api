package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.finance.domain.FinancialAmortizationMethod;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentType;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEventEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialInstallmentEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialPaymentEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FinancingOperations {
    List<Offer> offers(UUID userId,CareerGame game,UUID careerId,FinancialProductType productType,BigDecimal requestedAmount);
    ContractDetails create(UUID userId,CareerGame game,UUID careerId,CreateContractCommand command);
    List<ContractDetails> list(UUID userId,CareerGame game,UUID careerId);
    ContractDetails get(UUID userId,CareerGame game,UUID careerId,UUID contractId);
    ContractDetails pay(UUID userId,CareerGame game,UUID careerId,UUID contractId,PaymentCommand command);

    record CreateContractCommand(UUID operationId,FinancialProductType productType,BigDecimal requestedAmount,int termPeriods,
                                 Integer expectedOperationalWeek,Integer expectedPayrollMonth,BigDecimal expectedBalance) {}
    record PaymentCommand(UUID operationId,FinancialPaymentType paymentType,BigDecimal amount,Integer expectedOperationalWeek,
                          Integer expectedPayrollMonth,BigDecimal expectedBalance) {}
    record Offer(FinancialProductType productType,String policyVersion,String policySource,LocalDate policyReferenceAsOf,String rateBasis,
                 String jurisdictionCountryCode,String jurisdictionStateCode,String jurisdictionCity,String displayCurrency,
                 BigDecimal requestedAmount,BigDecimal principal,BigDecimal downPayment,BigDecimal annualInterestRate,
                 FinancialAmortizationMethod amortizationMethod,FinancialPaymentFrequency paymentFrequency,int termPeriods,
                 BigDecimal installmentAmount,BigDecimal expectedTotalCost) {}
    record ContractDetails(FinancialContractEntity contract,List<FinancialInstallmentEntity> installments,
                           List<FinancialPaymentEntity> payments,List<FinancialContractEventEntity> events) {}
}
