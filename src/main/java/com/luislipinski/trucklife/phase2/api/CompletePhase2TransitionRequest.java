package com.luislipinski.trucklife.phase2.api;

import com.luislipinski.trucklife.phase2.domain.Phase2AffiliationChoice;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.UUID;

public record CompletePhase2TransitionRequest(
        @NotNull UUID operationId,
        @NotNull Phase2AffiliationChoice affiliation,
        @Size(max = 160) String carrierName,
        @Size(min = 2, max = 10) String carrierCountryCode,
        @Size(min = 2, max = 10) String carrierStateCode,
        @NotNull DayOfWeek effectiveDay,
        @NotNull BigDecimal expectedBalance
) {
}
