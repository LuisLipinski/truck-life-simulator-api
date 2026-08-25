package com.luislipinski.trucklife.career.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRepository extends JpaRepository<CareerEntity, UUID> {

    List<CareerEntity> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<CareerEntity> findByIdAndUserId(UUID id, UUID userId);
}
