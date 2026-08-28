package com.luislipinski.trucklife.finance.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record BalanceAdjustmentRequest(
        @NotNull UUID operationId,
        @Min(1) Integer expectedOperationalWeek,
        @Min(1) Integer expectedPayrollMonth,
        @NotNull @Digits(integer=12,fraction=2) BigDecimal expectedBalance,
        @NotNull @Digits(integer=12,fraction=2) BigDecimal newBalance,
        @Size(max=180) String note
) {}
