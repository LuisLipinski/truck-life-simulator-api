ALTER TABLE careers
    ADD COLUMN dangerous_goods_qualified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE academy_progress (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    target_level SMALLINT NOT NULL,
    module_code VARCHAR(60) NOT NULL,
    module_name VARCHAR(160) NOT NULL,
    required_distance NUMERIC(14,2) NOT NULL,
    distance_at_completion NUMERIC(14,2) NOT NULL,
    fee_amount NUMERIC(14,2) NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    operational_week INTEGER NOT NULL,
    policy_version VARCHAR(60) NOT NULL,
    context_snapshot_json TEXT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_academy_progress_career
        FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_academy_progress_target_level CHECK (target_level IN (2, 3)),
    CONSTRAINT chk_academy_progress_module CHECK (
        module_code IN ('TRUCK_DRIVING_PROFICIENCY', 'DOUBLE_TRAILER_HANDLING')
    ),
    CONSTRAINT chk_academy_progress_distance CHECK (
        required_distance > 0 AND distance_at_completion >= required_distance
    ),
    CONSTRAINT chk_academy_progress_fee CHECK (fee_amount >= 0),
    CONSTRAINT chk_academy_progress_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_academy_progress_week CHECK (operational_week > 0),
    CONSTRAINT chk_academy_progress_snapshot CHECK (
        BTRIM(context_snapshot_json) <> ''
        AND jsonb_typeof(context_snapshot_json::jsonb) = 'object'
    ),
    CONSTRAINT uq_academy_progress_career_level UNIQUE (career_id, target_level)
);

CREATE INDEX idx_academy_progress_career_completed
    ON academy_progress (career_id, completed_at, id);

CREATE TABLE qualifications (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    qualification_type VARCHAR(20) NOT NULL,
    qualification_name VARCHAR(80) NOT NULL,
    fee_amount NUMERIC(14,2) NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    operational_week INTEGER NOT NULL,
    policy_version VARCHAR(60) NOT NULL,
    context_snapshot_json TEXT NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_qualifications_career
        FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_qualifications_type CHECK (qualification_type IN ('HAZMAT', 'ADR')),
    CONSTRAINT chk_qualifications_name CHECK (BTRIM(qualification_name) <> ''),
    CONSTRAINT chk_qualifications_fee CHECK (fee_amount >= 0),
    CONSTRAINT chk_qualifications_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_qualifications_week CHECK (operational_week > 0),
    CONSTRAINT chk_qualifications_snapshot CHECK (
        BTRIM(context_snapshot_json) <> ''
        AND jsonb_typeof(context_snapshot_json::jsonb) = 'object'
    ),
    CONSTRAINT uq_qualifications_career_type UNIQUE (career_id, qualification_type)
);

CREATE INDEX idx_qualifications_career_acquired
    ON qualifications (career_id, acquired_at, id);
