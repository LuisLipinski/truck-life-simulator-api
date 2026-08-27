package com.luislipinski.trucklife.incident.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.incident.application.IncidentOperations;
import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.domain.IncidentStatus;
import com.luislipinski.trucklife.incident.domain.IncidentType;
import com.luislipinski.trucklife.incident.persistence.IncidentEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID careerId,
        CareerGame game,
        UUID relatedTripId,
        int operationalWeek,
        IncidentType type,
        BigDecimal amount,
        BigDecimal remainingAmount,
        String route,
        String description,
        IncidentChargeMethod chargeMethod,
        IncidentStatus status,
        Instant recordedAt,
        Instant updatedAt,
        long version,
        List<DeductionResponse> deductions
) {

    static IncidentResponse from(IncidentOperations.Result result, CareerGame game) {
        IncidentEntity incident = result.incident();
        return new IncidentResponse(
                incident.getId(),
                incident.getCareerId(),
                game,
                incident.getRelatedTripId(),
                incident.getOperationalWeek(),
                incident.getType(),
                incident.getAmount(),
                incident.getRemainingAmount(),
                incident.getRouteLabel(),
                incident.getDescription(),
                incident.getChargeMethod(),
                incident.getStatus(),
                incident.getRecordedAt(),
                incident.getUpdatedAt(),
                incident.getVersion(),
                result.deductions().stream()
                        .map(deduction -> new DeductionResponse(
                                deduction.getId(),
                                deduction.getPayslipId(),
                                deduction.getAmount(),
                                deduction.getRecordedAt()
                        ))
                        .toList()
        );
    }

    public record DeductionResponse(
            UUID id,
            UUID payslipId,
            BigDecimal amount,
            Instant recordedAt
    ) {
    }
}
