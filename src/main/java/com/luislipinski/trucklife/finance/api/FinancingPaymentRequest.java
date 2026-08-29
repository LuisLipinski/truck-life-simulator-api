package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.domain.FinancialPaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record FinancingPaymentRequest(
        @NotNull UUID operationId,
        @NotNull FinancialPaymentType paymentType,
        @DecimalMin("0.01") BigDecimal amount,
        @NotNull @Min(1) Integer expectedOperationalWeek,
        @Min(1) Integer expectedPayrollMonth,
        @NotNull BigDecimal expectedBalance
) {}
