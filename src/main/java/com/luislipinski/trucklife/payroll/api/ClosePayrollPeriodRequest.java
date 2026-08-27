package com.luislipinski.trucklife.payroll.api;

import jakarta.validation.constraints.Min;

public record ClosePayrollPeriodRequest(
        @Min(value = 1, message = "expectedOperationalWeek must be greater than zero")
        int expectedOperationalWeek
) {
}
