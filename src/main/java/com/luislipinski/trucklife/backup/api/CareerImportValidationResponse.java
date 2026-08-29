package com.luislipinski.trucklife.backup.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import java.math.BigDecimal;
import java.util.UUID;

public record CareerImportValidationResponse(
        UUID operationId,
        String sourceCareerId,
        CareerGame game,
        int sourceVersion,
        boolean valid,
        boolean persisted,
        Summary summary
) {
    public record Summary(
            String driverName,
            String baseCity,
            String companyName,
            short currentLevel,
            BigDecimal balance,
            int currentOperationalWeek,
            Integer currentPayrollMonth,
            int trips,
            int closedPeriods,
            int incidents,
            int careerEvents,
            int customExpenses
    ) {
    }
}
