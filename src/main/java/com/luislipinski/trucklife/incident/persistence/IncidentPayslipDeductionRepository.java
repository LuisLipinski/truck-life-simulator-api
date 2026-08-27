package com.luislipinski.trucklife.incident.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentPayslipDeductionRepository
        extends JpaRepository<IncidentPayslipDeductionEntity, UUID> {

    List<IncidentPayslipDeductionEntity> findAllByIncidentIdOrderByRecordedAtAscIdAsc(UUID incidentId);
}
