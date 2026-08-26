package com.luislipinski.trucklife.career.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCareerCommand(
        CareerGame game,
        String driverName,
        String companyName,
        String biography,
        BigDecimal initialBalance,
        String baseCurrency,
        String displayCurrency,
        BigDecimal exchangeRate,
        LocalDate exchangeRateAsOf,
        String stateCode,
        String countryCode,
        String baseCity,
        String defaultTruckMake,
        String defaultTruckModel,
        String cityMarketVersion,
        String cityMarketLabel,
        BigDecimal cityCostFactor,
        BigDecimal citySalaryFactor
) {
}
