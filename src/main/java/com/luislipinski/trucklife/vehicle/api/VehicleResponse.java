package com.luislipinski.trucklife.vehicle.api;

import com.luislipinski.trucklife.vehicle.application.VehicleOperations;
import com.luislipinski.trucklife.vehicle.domain.VehicleAcquisitionType;
import com.luislipinski.trucklife.vehicle.domain.VehicleCondition;
import com.luislipinski.trucklife.vehicle.domain.VehicleStatus;
import com.luislipinski.trucklife.vehicle.persistence.CareerVehicleEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String make,
        String model,
        Short modelYear,
        BigDecimal initialOdometer,
        BigDecimal purchasePrice,
        String displayCurrency,
        VehicleCondition vehicleCondition,
        String purchaseCountryCode,
        String purchaseStateCode,
        VehicleAcquisitionType acquisitionType,
        VehicleStatus status,
        int acquiredOperationalWeek,
        Integer acquiredPayrollMonth,
        UUID financingContractId,
        short currentPhase,
        boolean phase2Unlocked,
        BigDecimal currentBalance,
        Instant createdAt,
        long version
) {
    static VehicleResponse from(VehicleOperations.VehicleDetails details) {
        CareerVehicleEntity vehicle = details.vehicle();
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getModelYear(),
                vehicle.getInitialOdometer(),
                vehicle.getPurchasePrice(),
                vehicle.getDisplayCurrency(),
                vehicle.getVehicleCondition(),
                vehicle.getPurchaseCountryCode(),
                vehicle.getPurchaseStateCode(),
                vehicle.getAcquisitionType(),
                vehicle.getStatus(),
                vehicle.getAcquiredOperationalWeek(),
                vehicle.getAcquiredPayrollMonth(),
                details.financingContractId(),
                details.currentPhase(),
                details.currentPhase() >= 2,
                details.currentBalance(),
                vehicle.getCreatedAt(),
                vehicle.getVersion()
        );
    }
}
