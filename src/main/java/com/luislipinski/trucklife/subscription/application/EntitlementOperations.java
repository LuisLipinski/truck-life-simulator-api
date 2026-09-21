package com.luislipinski.trucklife.subscription.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.PlanFeatureCode;
import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface EntitlementOperations {
    EntitlementSnapshot entitlements(UUID userId);
    FeatureAccess careerLimit(UUID userId, CareerGame game);
    List<PlanSnapshot> plans();

    record FeatureAccess(boolean enabled, Integer limit) {}

    record EntitlementSnapshot(
            PlanCode plan,
            boolean premium,
            SubscriptionStatus subscriptionStatus,
            Instant currentPeriodEnd,
            Map<PlanFeatureCode, FeatureAccess> features
    ) {
        public FeatureAccess feature(PlanFeatureCode code) {
            return features.getOrDefault(code, new FeatureAccess(false, null));
        }
    }

    record PlanSnapshot(
            PlanCode code,
            String name,
            boolean active,
            Integer priceCents,
            String currency,
            String billingPeriod,
            Map<PlanFeatureCode, FeatureAccess> features
    ) {}
}
