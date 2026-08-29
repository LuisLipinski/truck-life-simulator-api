package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateFinancingContractRequest(
        @NotNull UUID operationId,
        @NotNull FinancialProductType productType,
        @NotNull @DecimalMin("1.00") BigDecimal requestedAmount,
        @Min(1) int termPeriods,
        @NotNull @Min(1) Integer expectedOperationalWeek,
        @Min(1) Integer expectedPayrollMonth,
        @NotNull BigDecimal expectedBalance
) {}
