package com.luislipinski.trucklife.trip.api;

import com.luislipinski.trucklife.trip.application.CreateTripCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

public record CreateTripRequest(
        @NotBlank @Size(max = 10) String departureDay,
        @NotNull LocalTime departureTime,
        @NotBlank @Size(max = 10) String arrivalDay,
        @NotNull LocalTime arrivalTime,
        @NotBlank @Size(max = 160) String originCity,
        @Size(max = 160) String originCompany,
        @NotBlank @Size(max = 160) String destinationCity,
        @Size(max = 160) String destinationCompany,
        @Size(max = 200) String cargo,
        @NotBlank @Size(max = 20) String type,
        @Size(max = 30) String paymentCategory,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal officialDistance,
        @Min(0) Integer breakMinutes,
        @Size(max = 80) String truckMake,
        @Size(max = 120) String truckModel,
        @DecimalMin(value = "0") BigDecimal odometerStart,
        @DecimalMin(value = "0") BigDecimal odometerEnd
) {

    CreateTripCommand toCommand() {
        return new CreateTripCommand(
                departureDay,
                departureTime,
                arrivalDay,
                arrivalTime,
                originCity,
                originCompany,
                destinationCity,
                destinationCompany,
                cargo,
                type,
                paymentCategory,
                officialDistance,
                breakMinutes,
                truckMake,
                truckModel,
                odometerStart,
                odometerEnd
        );
    }
}
