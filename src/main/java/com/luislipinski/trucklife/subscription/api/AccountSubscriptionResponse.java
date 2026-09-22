package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.subscription.application.EntitlementOperations;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import com.luislipinski.trucklife.subscription.persistence.PaymentOrderEntity;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionEntity;
import java.time.Instant;
import java.util.List;

public record AccountSubscriptionResponse(
        PlanCode plan,
        boolean premium,
        SubscriptionStatus subscriptionStatus,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        List<PaymentOrderResponse> payments
) {
    static AccountSubscriptionResponse from(
            EntitlementOperations.EntitlementSnapshot entitlements,
            SubscriptionEntity subscription,
            List<PaymentOrderEntity> payments
    ) {
        return new AccountSubscriptionResponse(
                entitlements.plan(),
                entitlements.premium(),
                subscription == null ? entitlements.subscriptionStatus() : subscription.getStatus(),
                subscription == null ? null : subscription.getCurrentPeriodStart(),
                subscription == null ? entitlements.currentPeriodEnd() : subscription.getCurrentPeriodEnd(),
                subscription != null && subscription.isCancelAtPeriodEnd(),
                payments.stream().map(PaymentOrderResponse::from).toList()
        );
    }
}
