package com.luislipinski.trucklife.finance.domain;

public enum FinancialPaymentFrequency {
    WEEKLY(52),
    BIWEEKLY(26),
    MONTHLY(12);

    private final int periodsPerYear;

    FinancialPaymentFrequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int periodsPerYear() {
        return periodsPerYear;
    }
}
