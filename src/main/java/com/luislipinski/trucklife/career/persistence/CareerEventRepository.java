package com.luislipinski.trucklife.career.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerEventRepository extends JpaRepository<CareerEventEntity, UUID> {

    List<CareerEventEntity> findAllByCareerIdOrderByEffectiveDateAscRecordedAtAscIdAsc(UUID careerId);
}
