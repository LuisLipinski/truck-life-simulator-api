package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryEntity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public record LedgerEntryResponse(UUID id,UUID careerId,LedgerEntryType type,LedgerSourceType sourceType,UUID sourceId,
                                  int operationalWeek,Integer payrollMonth,BigDecimal amount,BigDecimal balanceDelta,BigDecimal reserveDelta,
                                  BigDecimal balanceBefore,BigDecimal balanceAfter,BigDecimal reserveBalanceBefore,BigDecimal reserveBalanceAfter,
                                  String displayCurrency,String description,Map<String,Object> metadata,Instant recordedAt) {
    private static final TypeReference<Map<String,Object>> MAP_TYPE=new TypeReference<>(){};
    static LedgerEntryResponse from(LedgerEntryEntity e,ObjectMapper mapper){return new LedgerEntryResponse(e.getId(),e.getCareerId(),e.getEntryType(),e.getSourceType(),e.getSourceId(),e.getOperationalWeek(),e.getPayrollMonth(),e.getAmount(),e.getBalanceDelta(),e.getReserveDelta(),e.getBalanceBefore(),e.getBalanceAfter(),e.getReserveBalanceBefore(),e.getReserveBalanceAfter(),e.getDisplayCurrency(),e.getDescription(),map(e.getMetadataJson(),mapper),e.getRecordedAt());}
    private static Map<String,Object> map(String json,ObjectMapper mapper){try{return mapper.readValue(json.getBytes(StandardCharsets.UTF_8),MAP_TYPE);}catch(JacksonException ex){throw new IllegalStateException("Ledger metadata could not be deserialized",ex);}}
}
