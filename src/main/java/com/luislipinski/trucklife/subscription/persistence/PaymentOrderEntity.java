package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.PaymentOrderStatus;
import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
public class PaymentOrderEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "subscription_id", nullable = false) private UUID subscriptionId;
    @Column(name = "checkout_operation_id", nullable = false) private UUID checkoutOperationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private PaymentProviderCode provider;
    @Column(name = "provider_order_id", length = 160) private String providerOrderId;
    @Column(name = "provider_payment_id", length = 160) private String providerPaymentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentOrderStatus status;
    @Column(name = "amount_cents", nullable = false) private int amountCents;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "pix_copy_paste", columnDefinition = "text") private String pixCopyPaste;
    @Column(name = "pix_qr_code_base64", columnDefinition = "text") private String pixQrCodeBase64;
    @Column(name = "pix_ticket_url", columnDefinition = "text") private String pixTicketUrl;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PaymentOrderEntity() {}

    public PaymentOrderEntity(
            UUID id,
            UUID userId,
            UUID subscriptionId,
            UUID checkoutOperationId,
            PaymentProviderCode provider,
            int amountCents,
            String currency,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.subscriptionId = subscriptionId;
        this.checkoutOperationId = checkoutOperationId;
        this.provider = provider;
        this.status = PaymentOrderStatus.CREATING;
        this.amountCents = amountCents;
        this.currency = currency;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getCheckoutOperationId() { return checkoutOperationId; }
    public PaymentProviderCode getProvider() { return provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public PaymentOrderStatus getStatus() { return status; }
    public int getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public String getPixCopyPaste() { return pixCopyPaste; }
    public String getPixQrCodeBase64() { return pixQrCodeBase64; }
    public String getPixTicketUrl() { return pixTicketUrl; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void markPending(
            String providerOrderId,
            String providerPaymentId,
            String pixCopyPaste,
            String pixQrCodeBase64,
            String pixTicketUrl,
            Instant expiresAt,
            Instant updatedAt
    ) {
        this.providerOrderId = providerOrderId;
        this.providerPaymentId = providerPaymentId;
        this.pixCopyPaste = pixCopyPaste;
        this.pixQrCodeBase64 = pixQrCodeBase64;
        this.pixTicketUrl = pixTicketUrl;
        this.expiresAt = expiresAt;
        this.status = PaymentOrderStatus.PENDING;
        this.updatedAt = updatedAt;
    }

    public void markFailed(Instant updatedAt) {
        this.status = PaymentOrderStatus.FAILED;
        this.updatedAt = updatedAt;
    }
}
