package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.subscription.application.EntitlementOperations;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.PlanFeatureCode;
import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record EntitlementsResponse(
        PlanCode plan,
        boolean premium,
        SubscriptionStatus subscriptionStatus,
        Instant currentPeriodEnd,
        Map<PlanFeatureCode, EntitlementFeatureResponse> features
) {
    static EntitlementsResponse from(EntitlementOperations.EntitlementSnapshot snapshot) {
        Map<PlanFeatureCode, EntitlementFeatureResponse> features = new LinkedHashMap<>();
        snapshot.features().forEach((code, access) -> features.put(code, EntitlementFeatureResponse.from(access)));
        return new EntitlementsResponse(
                snapshot.plan(),
                snapshot.premium(),
                snapshot.subscriptionStatus(),
                snapshot.currentPeriodEnd(),
                Map.copyOf(features)
        );
    }
}
