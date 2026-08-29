package com.luislipinski.trucklife.backup.persistence;

import com.luislipinski.trucklife.career.domain.CareerGame;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerImportOperationRepository extends JpaRepository<CareerImportOperationEntity, UUID> {

    Optional<CareerImportOperationEntity> findByUserIdAndOperationId(UUID userId, UUID operationId);

    Optional<CareerImportOperationEntity> findByUserIdAndGameAndSourceCareerId(
            UUID userId,
            CareerGame game,
            String sourceCareerId
    );

    Optional<CareerImportOperationEntity> findByIdAndUserId(UUID id, UUID userId);
}
