package com.luislipinski.trucklife.ledger.application;

import com.luislipinski.trucklife.ledger.persistence.LedgerEntryEntity;

public interface LedgerWriter {
    LedgerEntryEntity record(LedgerEntryDraft draft);
}
