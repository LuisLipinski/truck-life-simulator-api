package com.luislipinski.trucklife.phase2.persistence;

import com.luislipinski.trucklife.phase2.domain.CarrierContractStatus;
import com.luislipinski.trucklife.phase2.domain.Phase2AffiliationChoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carrier_contracts")
public class CarrierContractEntity {
    @Id private UUID id;
    @Column(name = "career_id", nullable = false) private UUID careerId;
    @Column(name = "transition_id", nullable = false, unique = true) private UUID transitionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CarrierContractStatus status;
    @Column(name = "carrier_name", nullable = false, length = 160) private String carrierName;
    @Enumerated(EnumType.STRING) @Column(name = "affiliation_origin", nullable = false, length = 30) private Phase2AffiliationChoice affiliationOrigin;
    @Column(name = "jurisdiction_country_code", length = 10) private String jurisdictionCountryCode;
    @Column(name = "jurisdiction_state_code", length = 10) private String jurisdictionStateCode;
    @Column(name = "compensation_rule_key", nullable = false, length = 40) private String compensationRuleKey;
    @Column(name = "compensation_policy_status", nullable = false, length = 40) private String compensationPolicyStatus;
    @Column(name = "started_operational_week", nullable = false) private int startedOperationalWeek;
    @Enumerated(EnumType.STRING) @Column(name = "started_effective_day", nullable = false, length = 9) private DayOfWeek startedEffectiveDay;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected CarrierContractEntity() {}

    public CarrierContractEntity(
            UUID id,
            UUID careerId,
            UUID transitionId,
            String carrierName,
            Phase2AffiliationChoice affiliationOrigin,
            String jurisdictionCountryCode,
            String jurisdictionStateCode,
            String compensationRuleKey,
            int startedOperationalWeek,
            DayOfWeek startedEffectiveDay,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.careerId = careerId;
        this.transitionId = transitionId;
        this.status = CarrierContractStatus.ACTIVE;
        this.carrierName = carrierName;
        this.affiliationOrigin = affiliationOrigin;
        this.jurisdictionCountryCode = jurisdictionCountryCode;
        this.jurisdictionStateCode = jurisdictionStateCode;
        this.compensationRuleKey = compensationRuleKey;
        this.compensationPolicyStatus = "JURISDICTION_SELECTED";
        this.startedOperationalWeek = startedOperationalWeek;
        this.startedEffectiveDay = startedEffectiveDay;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId(){return id;}
    public UUID getCareerId(){return careerId;}
    public UUID getTransitionId(){return transitionId;}
    public CarrierContractStatus getStatus(){return status;}
    public String getCarrierName(){return carrierName;}
    public Phase2AffiliationChoice getAffiliationOrigin(){return affiliationOrigin;}
    public String getJurisdictionCountryCode(){return jurisdictionCountryCode;}
    public String getJurisdictionStateCode(){return jurisdictionStateCode;}
    public String getCompensationRuleKey(){return compensationRuleKey;}
    public String getCompensationPolicyStatus(){return compensationPolicyStatus;}
    public int getStartedOperationalWeek(){return startedOperationalWeek;}
    public DayOfWeek getStartedEffectiveDay(){return startedEffectiveDay;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public long getVersion(){return version;}
}
