ALTER TABLE career_vehicles
    ADD COLUMN vehicle_condition VARCHAR(12),
    ADD COLUMN purchase_country_code VARCHAR(2),
    ADD COLUMN purchase_state_code VARCHAR(2),
    ADD CONSTRAINT chk_career_vehicles_condition
        CHECK (vehicle_condition IS NULL OR vehicle_condition IN ('NEW','USED')),
    ADD CONSTRAINT chk_career_vehicles_purchase_country
        CHECK (purchase_country_code IS NULL OR purchase_country_code ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT chk_career_vehicles_purchase_state
        CHECK (purchase_state_code IS NULL OR purchase_state_code ~ '^[A-Z]{2}$');

CREATE INDEX idx_career_vehicles_purchase_jurisdiction
    ON career_vehicles (purchase_country_code, purchase_state_code);
