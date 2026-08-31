package com.luislipinski.trucklife.payroll.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdatePayrollSettingsRequest(
        @Min(1) int expectedOperationalWeek,
        @Min(1) Integer expectedPayrollMonth,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal level1Gross,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal routeOverrunRate,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal benefits,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal perDiemRate
) {}
