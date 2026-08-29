package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.domain.FinancialPaymentType;
import com.luislipinski.trucklife.finance.persistence.FinancialPaymentEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancialPaymentResponse(UUID id,UUID operationId,FinancialPaymentType paymentType,BigDecimal amount,BigDecimal principalAmount,
        BigDecimal interestAmount,BigDecimal feeAmount,BigDecimal balanceBefore,BigDecimal balanceAfter,int operationalWeek,
        Integer payrollMonth,String displayCurrency,Instant recordedAt){
    static FinancialPaymentResponse from(FinancialPaymentEntity e){return new FinancialPaymentResponse(e.getId(),e.getOperationId(),e.getPaymentType(),e.getAmount(),e.getPrincipalAmount(),e.getInterestAmount(),e.getFeeAmount(),e.getBalanceBefore(),e.getBalanceAfter(),e.getOperationalWeek(),e.getPayrollMonth(),e.getDisplayCurrency(),e.getRecordedAt());}
}
