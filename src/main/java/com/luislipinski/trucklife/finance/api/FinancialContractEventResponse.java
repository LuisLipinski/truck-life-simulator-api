package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.finance.domain.FinancialContractEventType;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEventEntity;
import java.time.Instant;
import java.util.UUID;

public record FinancialContractEventResponse(UUID id,FinancialContractEventType eventType,int operationalWeek,Integer payrollMonth,String metadataJson,Instant recordedAt){
    static FinancialContractEventResponse from(FinancialContractEventEntity e){return new FinancialContractEventResponse(e.getId(),e.getEventType(),e.getOperationalWeek(),e.getPayrollMonth(),e.getMetadataJson(),e.getRecordedAt());}
}
