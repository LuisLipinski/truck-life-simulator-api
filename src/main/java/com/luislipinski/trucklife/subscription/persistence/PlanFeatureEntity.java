package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.PlanFeatureCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "plan_features")
public class PlanFeatureEntity {
    @Id private UUID id;
    @Column(name = "plan_id", nullable = false) private UUID planId;
    @Enumerated(EnumType.STRING) @Column(name = "feature_code", nullable = false, length = 80) private PlanFeatureCode featureCode;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "limit_value") private Integer limitValue;

    protected PlanFeatureEntity() {}

    public PlanFeatureEntity(UUID id, UUID planId, PlanFeatureCode featureCode, boolean enabled, Integer limitValue) {
        this.id = id;
        this.planId = planId;
        this.featureCode = featureCode;
        this.enabled = enabled;
        this.limitValue = limitValue;
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public PlanFeatureCode getFeatureCode() { return featureCode; }
    public boolean isEnabled() { return enabled; }
    public Integer getLimitValue() { return limitValue; }
}
