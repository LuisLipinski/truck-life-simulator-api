package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.application.FinanceOperations;
import com.luislipinski.trucklife.finance.domain.ExpenseType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FinanceResponse(UUID careerId, BigDecimal balance, String displayCurrency, int currentOperationalWeek,
                              Integer currentPayrollMonth, BigDecimal monthlyExpenseTotal, List<ExpenseResponse> expenses,
                              ReserveResponse emergencyReserve) {
    static FinanceResponse from(FinanceOperations.State state){
        var career=state.career(); var expenses=state.expenses();
        BigDecimal total=expenses.stream().filter(e -> e.isIncluded()).map(e -> e.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);
        return new FinanceResponse(career.getId(),career.getBalance(),career.getDisplayCurrency(),career.getCurrentOperationalWeek(),career.getCurrentPayrollMonth(),total,
                expenses.stream().map(e -> new ExpenseResponse(e.getId(),e.getExpenseType(),e.getCategory()==null?null:e.getCategory().code(),e.getName(),e.getAmount(),e.isIncluded(),e.getPolicyVersion(),e.getVersion())).toList(),
                new ReserveResponse(state.reserve().getBalance(),state.reserve().getAnnualYieldRate(),state.reserve().isAutoContributionEnabled(),state.reserve().getAutoContributionAmount(),state.reserve().getPolicyVersion(),state.reserve().getVersion()));
    }
    public record ExpenseResponse(UUID id, ExpenseType type, String category, String name, BigDecimal amount, boolean included, String policyVersion, long version) {}
    public record ReserveResponse(BigDecimal balance, BigDecimal annualYieldRate, boolean autoContributionEnabled, BigDecimal autoContributionAmount, String policyVersion, long version) {}
}
