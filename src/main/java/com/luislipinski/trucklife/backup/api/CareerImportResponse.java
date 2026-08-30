package com.luislipinski.trucklife.backup.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import java.math.BigDecimal;
import java.util.UUID;

public record CareerImportResponse(
        UUID operationId,
        String sourceCareerId,
        CareerGame game,
        int sourceVersion,
        UUID careerId,
        boolean persisted,
        boolean idempotentReplay,
        Summary summary
) {
    public record Summary(
            String driverName,
            String baseCity,
            String companyName,
            short currentLevel,
            BigDecimal balance,
            String baseCurrency,
            String displayCurrency,
            int currentOperationalWeek,
            Integer currentPayrollMonth
    ) {
    }
}
