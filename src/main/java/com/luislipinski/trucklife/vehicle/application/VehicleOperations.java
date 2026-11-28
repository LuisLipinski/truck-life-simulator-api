package com.luislipinski.trucklife.vehicle.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.vehicle.domain.VehicleAcquisitionType;
import com.luislipinski.trucklife.vehicle.persistence.CareerVehicleEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface VehicleOperations {
    List<VehicleDetails> list(UUID userId, CareerGame game, UUID careerId);
    VehicleDetails acquire(UUID userId, CareerGame game, UUID careerId, AcquireVehicleCommand command);

    record AcquireVehicleCommand(
            UUID operationId,
            VehicleAcquisitionType acquisitionType,
            String make,
            String model,
            Short modelYear,
            BigDecimal initialOdometer,
            BigDecimal purchasePrice,
            Integer financingTermPeriods,
            Integer expectedOperationalWeek,
            Integer expectedPayrollMonth,
            BigDecimal expectedBalance
    ) {}

    record VehicleDetails(
            CareerVehicleEntity vehicle,
            UUID financingContractId,
            short currentPhase,
            BigDecimal currentBalance
    ) {}
}
