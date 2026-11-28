package com.luislipinski.trucklife.vehicle.persistence;

import com.luislipinski.trucklife.vehicle.domain.VehicleStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerVehicleRepository extends JpaRepository<CareerVehicleEntity, UUID> {
    List<CareerVehicleEntity> findAllByCareerIdOrderByCreatedAtAscIdAsc(UUID careerId);
    Optional<CareerVehicleEntity> findByAcquisitionOperationId(UUID acquisitionOperationId);
    boolean existsByCareerIdAndStatus(UUID careerId, VehicleStatus status);
}
