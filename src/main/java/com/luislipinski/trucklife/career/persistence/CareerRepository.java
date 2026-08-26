package com.luislipinski.trucklife.career.persistence;

import com.luislipinski.trucklife.career.domain.CareerGame;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRepository extends JpaRepository<CareerEntity, UUID> {

    List<CareerEntity> findAllByUserIdAndGameOrderByCreatedAtAsc(UUID userId, CareerGame game);

    Optional<CareerEntity> findByIdAndUserIdAndGame(UUID id, UUID userId, CareerGame game);
}
