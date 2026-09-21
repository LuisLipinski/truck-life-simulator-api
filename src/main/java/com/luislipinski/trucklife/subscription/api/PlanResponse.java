package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.subscription.application.EntitlementOperations;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.PlanFeatureCode;
import java.util.LinkedHashMap;
import java.util.Map;

public record PlanResponse(
        PlanCode code,
        String name,
        boolean active,
        Integer priceCents,
        String currency,
        String billingPeriod,
        Map<PlanFeatureCode, EntitlementFeatureResponse> features
) {
    static PlanResponse from(EntitlementOperations.PlanSnapshot plan) {
        Map<PlanFeatureCode, EntitlementFeatureResponse> features = new LinkedHashMap<>();
        plan.features().forEach((code, access) -> features.put(code, EntitlementFeatureResponse.from(access)));
        return new PlanResponse(
                plan.code(),
                plan.name(),
                plan.active(),
                plan.priceCents(),
                plan.currency(),
                plan.billingPeriod(),
                Map.copyOf(features)
        );
    }
}
