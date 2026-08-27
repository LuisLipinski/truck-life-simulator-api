package com.luislipinski.trucklife.qualification.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyProgressRepository extends JpaRepository<AcademyProgressEntity, UUID> {
    List<AcademyProgressEntity> findAllByCareerIdOrderByTargetLevelAsc(UUID careerId);
    boolean existsByCareerIdAndTargetLevel(UUID careerId, short targetLevel);
}
