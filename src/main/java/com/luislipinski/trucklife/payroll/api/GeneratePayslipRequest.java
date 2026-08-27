package com.luislipinski.trucklife.payroll.api;

import jakarta.validation.constraints.Min;

public record GeneratePayslipRequest(
        @Min(value = 1, message = "expectedOperationalWeek must be greater than zero") Integer expectedOperationalWeek,
        @Min(value = 1, message = "expectedPayrollMonth must be greater than zero") Integer expectedPayrollMonth
) {}
