package com.luislipinski.trucklife.career.api;

import com.luislipinski.trucklife.career.application.CreateCareerCommand;
import com.luislipinski.trucklife.career.domain.CareerGame;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCareerRequest(
        @NotNull CareerGame game,
        @NotBlank @Size(max = 120) String driverName,
        @NotBlank @Size(max = 160) String companyName,
        @Size(max = 800) String biography,
        @NotNull @Digits(integer = 12, fraction = 2) BigDecimal initialBalance,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String baseCurrency,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String displayCurrency,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 10, fraction = 8) BigDecimal exchangeRate,
        LocalDate exchangeRateAsOf,
        @Size(max = 10) String stateCode,
        @Size(max = 10) String countryCode,
        @NotBlank @Size(max = 160) String baseCity,
        @Size(max = 80) String defaultTruckMake,
        @Size(max = 120) String defaultTruckModel,
        @Size(max = 40) String cityMarketVersion,
        @Size(max = 100) String cityMarketLabel,
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 4, fraction = 4) BigDecimal cityCostFactor,
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 4, fraction = 4) BigDecimal citySalaryFactor
) {

    CreateCareerCommand toCommand() {
        return new CreateCareerCommand(
                game,
                driverName,
                companyName,
                biography,
                initialBalance,
                baseCurrency,
                displayCurrency,
                exchangeRate,
                exchangeRateAsOf,
                stateCode,
                countryCode,
                baseCity,
                defaultTruckMake,
                defaultTruckModel,
                cityMarketVersion,
                cityMarketLabel,
                cityCostFactor,
                citySalaryFactor
        );
    }
}
