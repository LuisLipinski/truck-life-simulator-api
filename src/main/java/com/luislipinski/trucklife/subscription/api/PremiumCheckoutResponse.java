package com.luislipinski.trucklife.subscription.api;

public record PremiumCheckoutResponse(
        boolean idempotentReplay,
        PaymentOrderResponse payment
) {}
