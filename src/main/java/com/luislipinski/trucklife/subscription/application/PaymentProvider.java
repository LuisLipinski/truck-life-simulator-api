package com.luislipinski.trucklife.subscription.application;

import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import java.time.Instant;
import java.util.UUID;

public interface PaymentProvider {

    PaymentProviderCode code();

    boolean isConfigured();

    PixCheckout createPix(PixRequest request);

    record PixRequest(
            UUID operationId,
            UUID localPaymentOrderId,
            String payerEmail,
            int amountCents,
            String currency
    ) {}

    record PixCheckout(
            String providerOrderId,
            String providerPaymentId,
            String pixCopyPaste,
            String pixQrCodeBase64,
            String pixTicketUrl,
            Instant expiresAt
    ) {}
}
