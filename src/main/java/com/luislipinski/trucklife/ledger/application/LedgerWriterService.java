package com.luislipinski.trucklife.ledger.application;

import com.luislipinski.trucklife.ledger.persistence.LedgerEntryEntity;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class LedgerWriterService implements LedgerWriter {
    private final LedgerEntryRepository repository;
    private final ObjectMapper objectMapper;

    public LedgerWriterService(LedgerEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public LedgerEntryEntity record(LedgerEntryDraft draft) {
        LedgerEntryEntity existing = repository.findBySourceTypeAndSourceIdAndEntryType(
                draft.sourceType(), draft.sourceId(), draft.entryType()).orElse(null);
        if (existing != null) {
            if (!existing.getCareerId().equals(draft.careerId())) {
                throw new IllegalStateException("Ledger source identifier already belongs to another career");
            }
            return existing;
        }
        return repository.saveAndFlush(new LedgerEntryEntity(
                UUID.randomUUID(), draft.careerId(), draft.entryType(), draft.sourceType(), draft.sourceId(),
                draft.entryOrder(), draft.operationalWeek(), draft.payrollMonth(), money(draft.amount()),
                money(draft.balanceDelta()), money(draft.reserveDelta()), money(draft.balanceBefore()),
                money(draft.balanceAfter()), nullableMoney(draft.reserveBalanceBefore()), nullableMoney(draft.reserveBalanceAfter()),
                draft.displayCurrency(), description(draft.description()), json(draft.metadata()), draft.recordedAt()
        ));
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Ledger monetary values cannot be null");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
    private BigDecimal nullableMoney(BigDecimal value) { return value == null ? null : money(value); }
    private String description(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Ledger description cannot be blank");
        String result=value.strip(); return result.length()<=240?result:result.substring(0,240);
    }
    private String json(Map<String,Object> value) {
        try { return objectMapper.writeValueAsString(value==null?Map.of():value); }
        catch (JacksonException exception) { throw new IllegalStateException("Ledger metadata could not be serialized",exception); }
    }
}
