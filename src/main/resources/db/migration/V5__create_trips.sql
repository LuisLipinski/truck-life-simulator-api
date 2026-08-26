CREATE TABLE trips (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    operational_week INTEGER NOT NULL,
    departure_day VARCHAR(10) NOT NULL,
    departure_time TIME WITHOUT TIME ZONE NOT NULL,
    arrival_day VARCHAR(10) NOT NULL,
    arrival_time TIME WITHOUT TIME ZONE NOT NULL,
    origin_city VARCHAR(160) NOT NULL,
    origin_company VARCHAR(160),
    destination_city VARCHAR(160) NOT NULL,
    destination_company VARCHAR(160),
    cargo VARCHAR(200),
    trip_type VARCHAR(20) NOT NULL,
    payment_category VARCHAR(30) NOT NULL,
    official_distance NUMERIC(12, 2) NOT NULL,
    break_minutes INTEGER,
    truck_make VARCHAR(80),
    truck_model VARCHAR(120),
    odometer_start NUMERIC(14, 1),
    odometer_end NUMERIC(14, 1),
    source VARCHAR(20) NOT NULL,
    employer_snapshot_json TEXT NOT NULL,
    base_snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_trips_career
        FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_trips_operational_week CHECK (operational_week > 0),
    CONSTRAINT chk_trips_departure_day CHECK (
        departure_day IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    CONSTRAINT chk_trips_arrival_day CHECK (
        arrival_day IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    CONSTRAINT chk_trips_type CHECK (trip_type IN ('LOADED', 'DEADHEAD')),
    CONSTRAINT chk_trips_payment_category CHECK (
        payment_category IN ('NORMAL', 'HAZMAT', 'DOUBLES', 'HAZMAT_DOUBLES', 'DEADHEAD')
    ),
    CONSTRAINT chk_trips_official_distance CHECK (official_distance > 0),
    CONSTRAINT chk_trips_break_minutes CHECK (break_minutes IS NULL OR break_minutes >= 0),
    CONSTRAINT chk_trips_odometer_pair CHECK (
        (odometer_start IS NULL AND odometer_end IS NULL)
        OR (odometer_start IS NOT NULL AND odometer_end IS NOT NULL AND odometer_start >= 0 AND odometer_end >= odometer_start)
    ),
    CONSTRAINT chk_trips_source CHECK (source IN ('MANUAL', 'TELEMETRY', 'IMPORT')),
    CONSTRAINT chk_trips_employer_snapshot CHECK (
        BTRIM(employer_snapshot_json) <> ''
        AND jsonb_typeof(employer_snapshot_json::jsonb) = 'object'
    ),
    CONSTRAINT chk_trips_base_snapshot CHECK (
        BTRIM(base_snapshot_json) <> ''
        AND jsonb_typeof(base_snapshot_json::jsonb) = 'object'
    )
);

CREATE INDEX idx_trips_career_week_created_at
    ON trips (career_id, operational_week, created_at, id);
