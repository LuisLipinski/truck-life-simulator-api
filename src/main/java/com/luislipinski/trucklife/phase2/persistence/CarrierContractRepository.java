package com.luislipinski.trucklife.phase2.persistence;

import com.luislipinski.trucklife.phase2.domain.CarrierContractStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierContractRepository extends JpaRepository<CarrierContractEntity, UUID> {
    Optional<CarrierContractEntity> findByTransitionId(UUID transitionId);
    Optional<CarrierContractEntity> findByCareerIdAndStatus(UUID careerId, CarrierContractStatus status);
    List<CarrierContractEntity> findAllByCareerIdOrderByCreatedAtDescIdDesc(UUID careerId);
}
