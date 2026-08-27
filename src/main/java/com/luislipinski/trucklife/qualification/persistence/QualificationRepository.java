package com.luislipinski.trucklife.qualification.persistence;

import com.luislipinski.trucklife.qualification.domain.QualificationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualificationRepository extends JpaRepository<QualificationEntity, UUID> {
    List<QualificationEntity> findAllByCareerIdOrderByAcquiredAtAscIdAsc(UUID careerId);
    boolean existsByCareerIdAndType(UUID careerId, QualificationType type);
}
