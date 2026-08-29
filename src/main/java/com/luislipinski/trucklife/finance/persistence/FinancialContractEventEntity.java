package com.luislipinski.trucklife.finance.persistence;

import com.luislipinski.trucklife.finance.domain.FinancialContractEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="financial_contract_events")
public class FinancialContractEventEntity {
    @Id private UUID id;
    @Column(name="contract_id",nullable=false) private UUID contractId;
    @Enumerated(EnumType.STRING) @Column(name="event_type",nullable=false,length=30) private FinancialContractEventType eventType;
    @Column(name="operational_week",nullable=false) private int operationalWeek;
    @Column(name="payroll_month") private Integer payrollMonth;
    @Column(name="metadata_json",nullable=false,columnDefinition="text") private String metadataJson;
    @Column(name="recorded_at",nullable=false) private Instant recordedAt;
    protected FinancialContractEventEntity() {}
    public FinancialContractEventEntity(UUID id,UUID contractId,FinancialContractEventType eventType,int operationalWeek,Integer payrollMonth,String metadataJson,Instant recordedAt){this.id=id;this.contractId=contractId;this.eventType=eventType;this.operationalWeek=operationalWeek;this.payrollMonth=payrollMonth;this.metadataJson=metadataJson;this.recordedAt=recordedAt;}
    public UUID getId(){return id;} public UUID getContractId(){return contractId;} public FinancialContractEventType getEventType(){return eventType;}
    public int getOperationalWeek(){return operationalWeek;} public Integer getPayrollMonth(){return payrollMonth;} public String getMetadataJson(){return metadataJson;} public Instant getRecordedAt(){return recordedAt;}
}
