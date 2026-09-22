package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.subscription.domain.PaymentOrderStatus;
import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import com.luislipinski.trucklife.subscription.persistence.PaymentOrderEntity;
import java.time.Instant;
import java.util.UUID;

public record PaymentOrderResponse(
        UUID id,
        UUID subscriptionId,
        PaymentProviderCode provider,
        PaymentOrderStatus status,
        int amountCents,
        String currency,
        String pixCopyPaste,
        String pixQrCodeBase64,
        String pixTicketUrl,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {
    static PaymentOrderResponse from(PaymentOrderEntity order) {
        return new PaymentOrderResponse(
                order.getId(),
                order.getSubscriptionId(),
                order.getProvider(),
                order.getStatus(),
                order.getAmountCents(),
                order.getCurrency(),
                order.getPixCopyPaste(),
                order.getPixQrCodeBase64(),
                order.getPixTicketUrl(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
