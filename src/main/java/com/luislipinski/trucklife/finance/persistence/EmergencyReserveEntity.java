package com.luislipinski.trucklife.finance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="emergency_reserve")
public class EmergencyReserveEntity {
    @Id @Column(name="career_id") private UUID careerId;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal balance;
    @Column(name="annual_yield_rate", nullable=false, precision=8, scale=6) private BigDecimal annualYieldRate;
    @Column(name="auto_contribution_enabled", nullable=false) private boolean autoContributionEnabled;
    @Column(name="auto_contribution_amount", nullable=false, precision=14, scale=2) private BigDecimal autoContributionAmount;
    @Column(name="display_currency", nullable=false, length=3) private String displayCurrency;
    @Column(name="policy_version", nullable=false, length=60) private String policyVersion;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;

    protected EmergencyReserveEntity() {}
    public EmergencyReserveEntity(UUID careerId, BigDecimal balance, BigDecimal annualYieldRate, boolean autoContributionEnabled,
                                  BigDecimal autoContributionAmount, String displayCurrency, String policyVersion, Instant updatedAt) {
        this.careerId=careerId; this.balance=balance; this.annualYieldRate=annualYieldRate; this.autoContributionEnabled=autoContributionEnabled;
        this.autoContributionAmount=autoContributionAmount; this.displayCurrency=displayCurrency; this.policyVersion=policyVersion; this.updatedAt=updatedAt;
    }
    public UUID getCareerId(){return careerId;} public BigDecimal getBalance(){return balance;} public BigDecimal getAnnualYieldRate(){return annualYieldRate;}
    public boolean isAutoContributionEnabled(){return autoContributionEnabled;} public BigDecimal getAutoContributionAmount(){return autoContributionAmount;}
    public String getDisplayCurrency(){return displayCurrency;} public String getPolicyVersion(){return policyVersion;} public Instant getUpdatedAt(){return updatedAt;}
    public long getVersion(){return version;}
    public void configure(boolean enabled, BigDecimal amount, Instant now){this.autoContributionEnabled=enabled; this.autoContributionAmount=amount; this.updatedAt=now;}
    public void deposit(BigDecimal amount, Instant now){this.balance=this.balance.add(amount); this.updatedAt=now;}
    public void withdraw(BigDecimal amount, Instant now){this.balance=this.balance.subtract(amount); this.updatedAt=now;}
}
