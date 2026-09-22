package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.PaymentEventStatus;
import com.luislipinski.trucklife.subscription.domain.PaymentProviderCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_events")
public class PaymentEventEntity {
    @Id private UUID id;
    @Column(name = "payment_order_id") private UUID paymentOrderId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private PaymentProviderCode provider;
    @Column(name = "provider_event_id", nullable = false, length = 160) private String providerEventId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb") private String payloadJson;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Enumerated(EnumType.STRING) @Column(name = "processing_status", nullable = false, length = 30) private PaymentEventStatus processingStatus;
    @Column(name = "error_code", length = 80) private String errorCode;

    protected PaymentEventEntity() {}

    public PaymentEventEntity(
            UUID id,
            UUID paymentOrderId,
            PaymentProviderCode provider,
            String providerEventId,
            String eventType,
            String payloadJson,
            Instant receivedAt
    ) {
        this.id = id;
        this.paymentOrderId = paymentOrderId;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.receivedAt = receivedAt;
        this.processingStatus = PaymentEventStatus.RECEIVED;
    }

    public UUID getId() { return id; }
    public UUID getPaymentOrderId() { return paymentOrderId; }
    public PaymentProviderCode getProvider() { return provider; }
    public String getProviderEventId() { return providerEventId; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public PaymentEventStatus getProcessingStatus() { return processingStatus; }
    public String getErrorCode() { return errorCode; }
}
