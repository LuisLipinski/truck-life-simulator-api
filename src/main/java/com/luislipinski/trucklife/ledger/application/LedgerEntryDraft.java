package com.luislipinski.trucklife.ledger.application;

import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LedgerEntryDraft(
        UUID careerId,
        LedgerEntryType entryType,
        LedgerSourceType sourceType,
        UUID sourceId,
        int entryOrder,
        int operationalWeek,
        Integer payrollMonth,
        BigDecimal amount,
        BigDecimal balanceDelta,
        BigDecimal reserveDelta,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        BigDecimal reserveBalanceBefore,
        BigDecimal reserveBalanceAfter,
        String displayCurrency,
        String description,
        Map<String, Object> metadata,
        Instant recordedAt
) {}
