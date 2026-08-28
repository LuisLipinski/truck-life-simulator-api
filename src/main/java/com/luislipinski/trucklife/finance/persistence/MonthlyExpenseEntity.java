package com.luislipinski.trucklife.finance.persistence;

import com.luislipinski.trucklife.finance.domain.ExpenseType;
import com.luislipinski.trucklife.finance.domain.MonthlyExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monthly_expenses")
public class MonthlyExpenseEntity {
    @Id private UUID id;
    @Column(name="career_id", nullable=false) private UUID careerId;
    @Enumerated(EnumType.STRING) @Column(name="expense_type", nullable=false, length=10) private ExpenseType expenseType;
    @Enumerated(EnumType.STRING) @Column(length=40) private MonthlyExpenseCategory category;
    @Column(nullable=false, length=120) private String name;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal amount;
    @Column(nullable=false) private boolean included;
    @Column(name="display_currency", nullable=false, length=3) private String displayCurrency;
    @Column(name="policy_version", nullable=false, length=60) private String policyVersion;
    @Column(name="context_snapshot_json", nullable=false, columnDefinition="text") private String contextSnapshotJson;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;

    protected MonthlyExpenseEntity() {}

    public MonthlyExpenseEntity(UUID id, UUID careerId, ExpenseType expenseType, MonthlyExpenseCategory category,
                                String name, BigDecimal amount, boolean included, String displayCurrency,
                                String policyVersion, String contextSnapshotJson, Instant createdAt, Instant updatedAt) {
        this.id=id; this.careerId=careerId; this.expenseType=expenseType; this.category=category; this.name=name;
        this.amount=amount; this.included=included; this.displayCurrency=displayCurrency; this.policyVersion=policyVersion;
        this.contextSnapshotJson=contextSnapshotJson; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }

    public UUID getId(){return id;} public UUID getCareerId(){return careerId;} public ExpenseType getExpenseType(){return expenseType;}
    public MonthlyExpenseCategory getCategory(){return category;} public String getName(){return name;} public BigDecimal getAmount(){return amount;}
    public boolean isIncluded(){return included;} public String getDisplayCurrency(){return displayCurrency;} public String getPolicyVersion(){return policyVersion;}
    public String getContextSnapshotJson(){return contextSnapshotJson;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public long getVersion(){return version;}

    public boolean isCustom(){ return expenseType == ExpenseType.CUSTOM; }
    public void update(BigDecimal amount, boolean included, String customName, Instant now) {
        this.amount=amount; this.included=included;
        if (isCustom() && customName != null && !customName.isBlank()) this.name=customName.strip();
        this.updatedAt=now;
    }
}
