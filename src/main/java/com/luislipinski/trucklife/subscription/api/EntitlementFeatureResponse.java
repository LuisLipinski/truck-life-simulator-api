package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.subscription.application.EntitlementOperations;

public record EntitlementFeatureResponse(boolean enabled, Integer limit) {
    static EntitlementFeatureResponse from(EntitlementOperations.FeatureAccess feature) {
        return new EntitlementFeatureResponse(feature.enabled(), feature.limit());
    }
}
