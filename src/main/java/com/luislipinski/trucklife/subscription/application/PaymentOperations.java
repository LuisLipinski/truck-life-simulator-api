package com.luislipinski.trucklife.subscription.application;

import com.luislipinski.trucklife.subscription.persistence.PaymentOrderEntity;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionEntity;
import java.util.List;
import java.util.UUID;

public interface PaymentOperations {
    CheckoutResult checkoutPremium(UUID userId, String payerEmail, UUID operationId);
    PaymentOrderEntity payment(UUID userId, UUID paymentOrderId);
    List<PaymentOrderEntity> payments(UUID userId);
    SubscriptionEntity currentSubscription(UUID userId);

    record CheckoutResult(PaymentOrderEntity order, boolean idempotentReplay) {}
}
