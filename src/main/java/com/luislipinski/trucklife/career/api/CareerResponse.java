package com.luislipinski.trucklife.career.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CareerResponse(
        UUID id,
        CareerGame game,
        String driverName,
        String companyName,
        String biography,
        short currentLevel,
        BigDecimal balance,
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
        BigDecimal citySalaryFactor,
        int currentOperationalWeek,
        Integer currentPayrollMonth,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    static CareerResponse from(CareerEntity career) {
        return new CareerResponse(
                career.getId(),
                career.getGame(),
                career.getDriverName(),
                career.getCompanyName(),
                career.getBiography(),
                career.getCurrentLevel(),
                career.getBalance(),
                career.getBaseCurrency(),
                career.getDisplayCurrency(),
                career.getExchangeRate(),
                career.getExchangeRateAsOf(),
                career.getStateCode(),
                career.getCountryCode(),
                career.getBaseCity(),
                career.getDefaultTruckMake(),
                career.getDefaultTruckModel(),
                career.getCityMarketVersion(),
                career.getCityMarketLabel(),
                career.getCityCostFactor(),
                career.getCitySalaryFactor(),
                career.getCurrentOperationalWeek(),
                career.getCurrentPayrollMonth(),
                career.getCreatedAt(),
                career.getUpdatedAt(),
                career.getVersion()
        );
    }
}
