package com.luislipinski.trucklife.ledger.application;

import com.luislipinski.trucklife.ledger.persistence.LedgerEntryEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface LedgerWriter {
    LedgerEntryEntity ensureOpeningBalance(UUID careerId, BigDecimal balance, String displayCurrency,
                                           Integer initialPayrollMonth, Instant createdAt);
    LedgerEntryEntity record(LedgerEntryDraft draft);
}
