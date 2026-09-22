CREATE TABLE payment_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    checkout_operation_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_order_id VARCHAR(160),
    provider_payment_id VARCHAR(160),
    status VARCHAR(30) NOT NULL,
    amount_cents INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    pix_copy_paste TEXT,
    pix_qr_code_base64 TEXT,
    pix_ticket_url TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_orders_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT uq_payment_orders_user_operation UNIQUE (user_id, checkout_operation_id),
    CONSTRAINT chk_payment_orders_provider CHECK (provider IN ('MERCADO_PAGO')),
    CONSTRAINT chk_payment_orders_status CHECK (status IN ('CREATING','PENDING','PAID','EXPIRED','CANCELED','FAILED')),
    CONSTRAINT chk_payment_orders_amount CHECK (amount_cents > 0),
    CONSTRAINT chk_payment_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payment_orders_paid_at CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL)
        OR (status <> 'PAID')
    )
);

CREATE UNIQUE INDEX uq_payment_orders_provider_order
    ON payment_orders (provider, provider_order_id)
    WHERE provider_order_id IS NOT NULL;

CREATE INDEX idx_payment_orders_user_created
    ON payment_orders (user_id, created_at DESC, id DESC);

CREATE INDEX idx_payment_orders_subscription_created
    ON payment_orders (subscription_id, created_at DESC, id DESC);

CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    payment_order_id UUID,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json JSONB NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    processing_status VARCHAR(30) NOT NULL,
    error_code VARCHAR(80),
    CONSTRAINT fk_payment_events_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id) ON DELETE SET NULL,
    CONSTRAINT uq_payment_events_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT chk_payment_events_provider CHECK (provider IN ('MERCADO_PAGO')),
    CONSTRAINT chk_payment_events_status CHECK (processing_status IN ('RECEIVED','PROCESSED','IGNORED','FAILED')),
    CONSTRAINT chk_payment_events_payload CHECK (jsonb_typeof(payload_json) = 'object')
);

CREATE INDEX idx_payment_events_order_received
    ON payment_events (payment_order_id, received_at DESC, id DESC);

CREATE INDEX idx_payment_events_provider_received
    ON payment_events (provider, received_at DESC, id DESC);
