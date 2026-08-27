package com.luislipinski.trucklife.incident.persistence;

import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {

    List<IncidentEntity> findAllByCareerIdOrderByRecordedAtDescIdDesc(UUID careerId);

    Optional<IncidentEntity> findByIdAndCareerId(UUID id, UUID careerId);

    List<IncidentEntity> findAllByCareerIdAndChargeMethodAndRemainingAmountGreaterThanOrderByRecordedAtAscIdAsc(
            UUID careerId,
            IncidentChargeMethod chargeMethod,
            BigDecimal remainingAmount
    );
}
