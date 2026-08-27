package com.luislipinski.trucklife.incident.api;

import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.domain.IncidentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateIncidentRequest(
        @NotNull
        @Min(value = 1, message = "expectedOperationalWeek must be greater than zero")
        Integer expectedOperationalWeek,

        @NotNull
        IncidentType type,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "amount supports at most 12 integer and 2 decimal digits")
        BigDecimal amount,

        UUID relatedTripId,

        @Size(max = 500, message = "route must contain at most 500 characters")
        String route,

        @NotBlank
        @Size(max = 1000, message = "description must contain at most 1000 characters")
        String description,

        @NotNull
        IncidentChargeMethod chargeMethod
) {
}
