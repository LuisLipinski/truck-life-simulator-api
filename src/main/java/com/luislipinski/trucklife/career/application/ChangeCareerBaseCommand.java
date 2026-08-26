package com.luislipinski.trucklife.career.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChangeCareerBaseCommand(
        long version,
        LocalDate effectiveDate,
        String stateCode,
        String countryCode,
        String baseCity,
        String baseCurrency,
        BigDecimal exchangeRate,
        LocalDate exchangeRateAsOf,
        String cityMarketVersion,
        String cityMarketLabel,
        BigDecimal cityCostFactor,
        BigDecimal citySalaryFactor
) {
}
