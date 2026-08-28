package com.luislipinski.trucklife.ledger.persistence;

import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {
    @Id private UUID id;
    @Column(name = "career_id", nullable = false) private UUID careerId;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, length = 40) private LedgerEntryType entryType;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 40) private LedgerSourceType sourceType;
    @Column(name = "source_id", nullable = false) private UUID sourceId;
    @Column(name = "entry_order", nullable = false) private int entryOrder;
    @Column(name = "operational_week", nullable = false) private int operationalWeek;
    @Column(name = "payroll_month") private Integer payrollMonth;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "balance_delta", nullable = false, precision = 14, scale = 2) private BigDecimal balanceDelta;
    @Column(name = "reserve_delta", nullable = false, precision = 14, scale = 2) private BigDecimal reserveDelta;
    @Column(name = "balance_before", nullable = false, precision = 14, scale = 2) private BigDecimal balanceBefore;
    @Column(name = "balance_after", nullable = false, precision = 14, scale = 2) private BigDecimal balanceAfter;
    @Column(name = "reserve_balance_before", precision = 14, scale = 2) private BigDecimal reserveBalanceBefore;
    @Column(name = "reserve_balance_after", precision = 14, scale = 2) private BigDecimal reserveBalanceAfter;
    @Column(name = "display_currency", nullable = false, length = 3) private String displayCurrency;
    @Column(nullable = false, length = 240) private String description;
    @Column(name = "metadata_json", nullable = false, columnDefinition = "text") private String metadataJson;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;

    protected LedgerEntryEntity() {}

    public LedgerEntryEntity(UUID id, UUID careerId, LedgerEntryType entryType, LedgerSourceType sourceType,
                             UUID sourceId, int entryOrder, int operationalWeek, Integer payrollMonth,
                             BigDecimal amount, BigDecimal balanceDelta, BigDecimal reserveDelta,
                             BigDecimal balanceBefore, BigDecimal balanceAfter, BigDecimal reserveBalanceBefore,
                             BigDecimal reserveBalanceAfter, String displayCurrency, String description,
                             String metadataJson, Instant recordedAt) {
        this.id=id; this.careerId=careerId; this.entryType=entryType; this.sourceType=sourceType; this.sourceId=sourceId;
        this.entryOrder=entryOrder; this.operationalWeek=operationalWeek; this.payrollMonth=payrollMonth; this.amount=amount;
        this.balanceDelta=balanceDelta; this.reserveDelta=reserveDelta; this.balanceBefore=balanceBefore; this.balanceAfter=balanceAfter;
        this.reserveBalanceBefore=reserveBalanceBefore; this.reserveBalanceAfter=reserveBalanceAfter; this.displayCurrency=displayCurrency;
        this.description=description; this.metadataJson=metadataJson; this.recordedAt=recordedAt;
    }

    public UUID getId(){return id;} public UUID getCareerId(){return careerId;} public LedgerEntryType getEntryType(){return entryType;}
    public LedgerSourceType getSourceType(){return sourceType;} public UUID getSourceId(){return sourceId;} public int getEntryOrder(){return entryOrder;}
    public int getOperationalWeek(){return operationalWeek;} public Integer getPayrollMonth(){return payrollMonth;} public BigDecimal getAmount(){return amount;}
    public BigDecimal getBalanceDelta(){return balanceDelta;} public BigDecimal getReserveDelta(){return reserveDelta;} public BigDecimal getBalanceBefore(){return balanceBefore;}
    public BigDecimal getBalanceAfter(){return balanceAfter;} public BigDecimal getReserveBalanceBefore(){return reserveBalanceBefore;} public BigDecimal getReserveBalanceAfter(){return reserveBalanceAfter;}
    public String getDisplayCurrency(){return displayCurrency;} public String getDescription(){return description;} public String getMetadataJson(){return metadataJson;}
    public Instant getRecordedAt(){return recordedAt;}
}
