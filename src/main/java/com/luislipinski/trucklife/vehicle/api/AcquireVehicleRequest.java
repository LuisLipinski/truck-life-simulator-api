package com.luislipinski.trucklife.vehicle.api;

import com.luislipinski.trucklife.vehicle.domain.VehicleAcquisitionType;
import com.luislipinski.trucklife.vehicle.domain.VehicleCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record AcquireVehicleRequest(
        @NotNull UUID operationId,
        @NotNull VehicleAcquisitionType acquisitionType,
        @NotNull VehicleCondition vehicleCondition,
        @Size(min = 2, max = 2) String purchaseCountryCode,
        @Size(min = 2, max = 2) String purchaseStateCode,
        @NotBlank @Size(max = 80) String make,
        @NotBlank @Size(max = 120) String model,
        @Min(1950) Short modelYear,
        @NotNull @DecimalMin("0.000") BigDecimal initialOdometer,
        @NotNull @DecimalMin("1.00") BigDecimal purchasePrice,
        @Min(1) Integer financingTermPeriods,
        @NotNull @Min(1) Integer expectedOperationalWeek,
        @Min(1) Integer expectedPayrollMonth,
        @NotNull BigDecimal expectedBalance
) {}
