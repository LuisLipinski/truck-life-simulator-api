package com.luislipinski.trucklife.incident.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.domain.IncidentType;
import com.luislipinski.trucklife.incident.persistence.IncidentEntity;
import com.luislipinski.trucklife.incident.persistence.IncidentPayslipDeductionEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface IncidentOperations {

    Result create(
            UUID userId,
            CareerGame game,
            UUID careerId,
            int expectedOperationalWeek,
            IncidentType type,
            BigDecimal amount,
            UUID relatedTripId,
            String route,
            String description,
            IncidentChargeMethod chargeMethod
    );

    List<Result> list(UUID userId, CareerGame game, UUID careerId);

    Result get(UUID userId, CareerGame game, UUID careerId, UUID incidentId);

    void cancel(UUID userId, CareerGame game, UUID careerId, UUID incidentId);

    record Result(
            IncidentEntity incident,
            List<IncidentPayslipDeductionEntity> deductions
    ) {
    }
}
