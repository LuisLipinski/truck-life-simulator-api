package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface FinancialLedgerOperations {
    List<LedgerEntryEntity> list(UUID userId, CareerGame game, UUID careerId, int limit);
    LedgerEntryEntity adjustBalance(UUID userId, CareerGame game, UUID careerId, UUID operationId,
                                    Integer expectedOperationalWeek, Integer expectedPayrollMonth,
                                    BigDecimal expectedBalance, BigDecimal newBalance, String note);
}
