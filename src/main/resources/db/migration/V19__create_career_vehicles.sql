ALTER TABLE careers
    ADD COLUMN current_phase SMALLINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_careers_current_phase CHECK (current_phase BETWEEN 1 AND 3);

CREATE TABLE career_vehicles (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    acquisition_operation_id UUID NOT NULL,
    make VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    model_year SMALLINT,
    initial_odometer NUMERIC(16,3) NOT NULL,
    purchase_price NUMERIC(14,2) NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    acquisition_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    acquired_operational_week INTEGER NOT NULL,
    acquired_payroll_month INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_career_vehicles_career FOREIGN KEY (career_id) REFERENCES careers(id) ON DELETE CASCADE,
    CONSTRAINT uq_career_vehicles_operation UNIQUE (acquisition_operation_id),
    CONSTRAINT chk_career_vehicles_make CHECK (char_length(trim(make)) BETWEEN 1 AND 80),
    CONSTRAINT chk_career_vehicles_model CHECK (char_length(trim(model)) BETWEEN 1 AND 120),
    CONSTRAINT chk_career_vehicles_year CHECK (model_year IS NULL OR model_year BETWEEN 1950 AND 2100),
    CONSTRAINT chk_career_vehicles_odometer CHECK (initial_odometer >= 0),
    CONSTRAINT chk_career_vehicles_price CHECK (purchase_price > 0),
    CONSTRAINT chk_career_vehicles_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_career_vehicles_acquisition_type CHECK (acquisition_type IN ('CASH','FINANCED')),
    CONSTRAINT chk_career_vehicles_status CHECK (status IN ('ACTIVE','SOLD')),
    CONSTRAINT chk_career_vehicles_week CHECK (acquired_operational_week > 0),
    CONSTRAINT chk_career_vehicles_month CHECK (acquired_payroll_month IS NULL OR acquired_payroll_month > 0)
);

CREATE INDEX idx_career_vehicles_career_created
    ON career_vehicles (career_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX uq_career_vehicles_active
    ON career_vehicles (career_id)
    WHERE status = 'ACTIVE';

ALTER TABLE financial_contracts
    ADD COLUMN vehicle_id UUID,
    ADD CONSTRAINT fk_financial_contracts_vehicle FOREIGN KEY (vehicle_id) REFERENCES career_vehicles(id),
    ADD CONSTRAINT chk_financial_contracts_vehicle_product
        CHECK (vehicle_id IS NULL OR product_type = 'VEHICLE_FINANCING');

CREATE UNIQUE INDEX uq_financial_contracts_vehicle
    ON financial_contracts (vehicle_id)
    WHERE vehicle_id IS NOT NULL;
