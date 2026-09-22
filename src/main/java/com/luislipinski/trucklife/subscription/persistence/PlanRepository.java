package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.PlanCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<PlanEntity, UUID> {
    Optional<PlanEntity> findByCodeAndActiveTrue(PlanCode code);
    List<PlanEntity> findAllByActiveTrueOrderByPriceCentsAscCodeAsc();
}
