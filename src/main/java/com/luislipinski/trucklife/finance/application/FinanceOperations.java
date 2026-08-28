package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.finance.persistence.EmergencyReserveEntity;
import com.luislipinski.trucklife.finance.persistence.MonthlyExpenseEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface FinanceOperations {
    State get(UUID userId, CareerGame game, UUID careerId);
    State createCustomExpense(UUID userId, CareerGame game, UUID careerId, Integer expectedWeek, Integer expectedMonth, String name, BigDecimal amount, boolean included);
    State updateExpense(UUID userId, CareerGame game, UUID careerId, UUID expenseId, Integer expectedWeek, Integer expectedMonth, String name, BigDecimal amount, boolean included);
    State deleteExpense(UUID userId, CareerGame game, UUID careerId, UUID expenseId, Integer expectedWeek, Integer expectedMonth);
    State applyExpenses(UUID userId, CareerGame game, UUID careerId, UUID operationId, Integer expectedWeek, Integer expectedMonth);
    State depositReserve(UUID userId, CareerGame game, UUID careerId, UUID operationId, Integer expectedWeek, Integer expectedMonth, BigDecimal amount);
    State withdrawReserve(UUID userId, CareerGame game, UUID careerId, UUID operationId, Integer expectedWeek, Integer expectedMonth, BigDecimal amount, String reason);
    State configureAutoReserve(UUID userId, CareerGame game, UUID careerId, Integer expectedWeek, Integer expectedMonth, boolean enabled, BigDecimal amount);
    record State(CareerEntity career, List<MonthlyExpenseEntity> expenses, EmergencyReserveEntity reserve) {}
}
