CREATE TABLE phase2_transitions (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    completion_operation_id UUID,
    status VARCHAR(20) NOT NULL,
    previous_company_name VARCHAR(160) NOT NULL,
    termination_reason VARCHAR(40) NOT NULL,
    termination_country_code VARCHAR(10),
    termination_state_code VARCHAR(10),
    employment_start_operational_week INTEGER NOT NULL,
    termination_operational_week INTEGER NOT NULL,
    termination_effective_day VARCHAR(9),
    statutory_settlement_amount NUMERIC(14,2) NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    policy_version VARCHAR(100) NOT NULL,
    policy_source VARCHAR(500) NOT NULL,
    policy_summary TEXT NOT NULL,
    policy_snapshot_json TEXT NOT NULL,
    selected_affiliation VARCHAR(30),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_phase2_transitions_career FOREIGN KEY (career_id) REFERENCES careers(id) ON DELETE CASCADE,
    CONSTRAINT uq_phase2_transitions_career UNIQUE (career_id),
    CONSTRAINT uq_phase2_transitions_operation UNIQUE (completion_operation_id),
    CONSTRAINT chk_phase2_transitions_status CHECK (status IN ('PENDING','COMPLETED')),
    CONSTRAINT chk_phase2_transitions_reason CHECK (termination_reason = 'VOLUNTARY_PHASE2_TRANSITION'),
    CONSTRAINT chk_phase2_transitions_start_week CHECK (employment_start_operational_week >= 1),
    CONSTRAINT chk_phase2_transitions_end_week CHECK (termination_operational_week >= employment_start_operational_week),
    CONSTRAINT chk_phase2_transitions_day CHECK (termination_effective_day IS NULL OR termination_effective_day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT chk_phase2_transitions_settlement CHECK (statutory_settlement_amount >= 0),
    CONSTRAINT chk_phase2_transitions_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_phase2_transitions_affiliation CHECK (selected_affiliation IS NULL OR selected_affiliation IN ('CURRENT_COMPANY','OTHER_COMPANY')),
    CONSTRAINT chk_phase2_transitions_completion CHECK (
        (status = 'PENDING' AND completion_operation_id IS NULL AND termination_effective_day IS NULL AND selected_affiliation IS NULL AND completed_at IS NULL)
        OR
        (status = 'COMPLETED' AND completion_operation_id IS NOT NULL AND termination_effective_day IS NOT NULL AND selected_affiliation IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_phase2_transitions_status ON phase2_transitions (status, updated_at DESC);

CREATE TABLE carrier_contracts (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    transition_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    carrier_name VARCHAR(160) NOT NULL,
    affiliation_origin VARCHAR(30) NOT NULL,
    jurisdiction_country_code VARCHAR(10),
    jurisdiction_state_code VARCHAR(10),
    compensation_rule_key VARCHAR(40) NOT NULL,
    compensation_policy_status VARCHAR(40) NOT NULL,
    started_operational_week INTEGER NOT NULL,
    started_effective_day VARCHAR(9) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_carrier_contracts_career FOREIGN KEY (career_id) REFERENCES careers(id) ON DELETE CASCADE,
    CONSTRAINT fk_carrier_contracts_transition FOREIGN KEY (transition_id) REFERENCES phase2_transitions(id) ON DELETE RESTRICT,
    CONSTRAINT uq_carrier_contracts_transition UNIQUE (transition_id),
    CONSTRAINT chk_carrier_contracts_status CHECK (status IN ('ACTIVE','CLOSED')),
    CONSTRAINT chk_carrier_contracts_origin CHECK (affiliation_origin IN ('CURRENT_COMPANY','OTHER_COMPANY')),
    CONSTRAINT chk_carrier_contracts_name CHECK (char_length(btrim(carrier_name)) BETWEEN 1 AND 160),
    CONSTRAINT chk_carrier_contracts_rule_key CHECK (char_length(btrim(compensation_rule_key)) BETWEEN 1 AND 40),
    CONSTRAINT chk_carrier_contracts_policy_status CHECK (compensation_policy_status = 'JURISDICTION_SELECTED'),
    CONSTRAINT chk_carrier_contracts_week CHECK (started_operational_week >= 1),
    CONSTRAINT chk_carrier_contracts_day CHECK (started_effective_day IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'))
);

CREATE INDEX idx_carrier_contracts_career_created ON carrier_contracts (career_id, created_at DESC);
CREATE UNIQUE INDEX uq_carrier_contracts_active ON carrier_contracts (career_id) WHERE status = 'ACTIVE';
