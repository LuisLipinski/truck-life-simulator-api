package com.luislipinski.trucklife.subscription.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "features.premium")
public class PremiumFeatureProperties {
    private boolean restrictionsEnabled;

    public boolean isRestrictionsEnabled() {
        return restrictionsEnabled;
    }

    public void setRestrictionsEnabled(boolean restrictionsEnabled) {
        this.restrictionsEnabled = restrictionsEnabled;
    }
}
