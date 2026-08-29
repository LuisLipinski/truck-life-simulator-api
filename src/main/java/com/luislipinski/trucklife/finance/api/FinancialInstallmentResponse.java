package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.domain.FinancialInstallmentStatus;
import com.luislipinski.trucklife.finance.persistence.FinancialInstallmentEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancialInstallmentResponse(UUID id,int scheduleVersion,int installmentNumber,Integer dueOperationalWeek,Integer duePayrollMonth,
        BigDecimal scheduledAmount,BigDecimal principalAmount,BigDecimal interestAmount,BigDecimal feeAmount,BigDecimal paidAmount,
        BigDecimal principalPaid,BigDecimal interestPaid,BigDecimal feePaid,FinancialInstallmentStatus status,Instant updatedAt){
    static FinancialInstallmentResponse from(FinancialInstallmentEntity e){return new FinancialInstallmentResponse(e.getId(),e.getScheduleVersion(),e.getInstallmentNumber(),e.getDueOperationalWeek(),e.getDuePayrollMonth(),e.getScheduledAmount(),e.getPrincipalAmount(),e.getInterestAmount(),e.getFeeAmount(),e.getPaidAmount(),e.getPrincipalPaid(),e.getInterestPaid(),e.getFeePaid(),e.getStatus(),e.getUpdatedAt());}
}
