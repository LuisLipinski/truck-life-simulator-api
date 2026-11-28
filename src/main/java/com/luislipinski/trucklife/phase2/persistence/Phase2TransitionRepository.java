package com.luislipinski.trucklife.phase2.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Phase2TransitionRepository extends JpaRepository<Phase2TransitionEntity, UUID> {
    Optional<Phase2TransitionEntity> findByCareerId(UUID careerId);
    Optional<Phase2TransitionEntity> findByCompletionOperationId(UUID operationId);
}
