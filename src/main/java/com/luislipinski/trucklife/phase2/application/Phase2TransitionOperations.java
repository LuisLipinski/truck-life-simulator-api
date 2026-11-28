package com.luislipinski.trucklife.phase2.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.phase2.domain.Phase2AffiliationChoice;
import com.luislipinski.trucklife.phase2.persistence.CarrierContractEntity;
import com.luislipinski.trucklife.phase2.persistence.Phase2TransitionEntity;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.UUID;

public interface Phase2TransitionOperations {
    Details getOrCreate(UUID userId, CareerGame game, UUID careerId);

    Details complete(UUID userId, CareerGame game, UUID careerId, CompleteCommand command);

    record CompleteCommand(
            UUID operationId,
            Phase2AffiliationChoice affiliation,
            String carrierName,
            String carrierCountryCode,
            String carrierStateCode,
            DayOfWeek effectiveDay,
            BigDecimal expectedBalance
    ) {}

    record Details(
            Phase2TransitionEntity transition,
            CarrierContractEntity carrierContract,
            BigDecimal currentBalance,
            boolean replay
    ) {}
}
