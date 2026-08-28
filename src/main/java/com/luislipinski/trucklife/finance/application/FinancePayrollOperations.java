package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.persistence.CareerEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface FinancePayrollOperations {
    PayrollReserveResult applyPayslipReserve(CareerEntity career, BigDecimal availableDeposit, UUID payslipId, Instant now);
    record PayrollReserveResult(BigDecimal interestAmount, BigDecimal contributionAmount, BigDecimal balanceCreditAmount,
                                BigDecimal reserveBalanceBefore, BigDecimal reserveBalanceAfter) {}
}
