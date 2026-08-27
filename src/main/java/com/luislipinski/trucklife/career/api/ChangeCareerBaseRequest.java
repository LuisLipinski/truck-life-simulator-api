package com.luislipinski.trucklife.career.api;

import com.luislipinski.trucklife.career.application.ChangeCareerBaseCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ChangeCareerBaseRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 9) String effectiveDay,
        @Size(max = 10) String stateCode,
        @Size(max = 10) String countryCode,
        @NotBlank @Size(max = 160) String baseCity,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String baseCurrency,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal exchangeRate,
        LocalDate exchangeRateAsOf,
        @Size(max = 40) String cityMarketVersion,
        @Size(max = 100) String cityMarketLabel,
        @DecimalMin(value = "0", inclusive = false) BigDecimal cityCostFactor,
        @DecimalMin(value = "0", inclusive = false) BigDecimal citySalaryFactor
) {

    ChangeCareerBaseCommand toCommand() {
        return new ChangeCareerBaseCommand(
                version,
                effectiveDay,
                stateCode,
                countryCode,
                baseCity,
                baseCurrency,
                exchangeRate,
                exchangeRateAsOf,
                cityMarketVersion,
                cityMarketLabel,
                cityCostFactor,
                citySalaryFactor
        );
    }
}
