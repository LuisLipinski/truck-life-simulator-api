package com.luislipinski.trucklife.trip.application;

import java.math.BigDecimal;
import java.time.LocalTime;

public record CreateTripCommand(
        String departureDay,
        LocalTime departureTime,
        String arrivalDay,
        LocalTime arrivalTime,
        String originCity,
        String originCompany,
        String destinationCity,
        String destinationCompany,
        String cargo,
        String type,
        String paymentCategory,
        BigDecimal officialDistance,
        Integer breakMinutes,
        String truckMake,
        String truckModel,
        BigDecimal odometerStart,
        BigDecimal odometerEnd
) {
}
