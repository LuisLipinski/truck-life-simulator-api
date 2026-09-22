package com.luislipinski.trucklife.subscription.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanFeatureRepository extends JpaRepository<PlanFeatureEntity, UUID> {
    List<PlanFeatureEntity> findAllByPlanIdOrderByFeatureCodeAsc(UUID planId);
}
