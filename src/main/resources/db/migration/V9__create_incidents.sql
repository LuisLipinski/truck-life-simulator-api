ALTER TABLE payslips
    ADD COLUMN incident_deduction_amount NUMERIC(14, 2) NOT NULL DEFAULT 0;

ALTER TABLE payslips
    ADD CONSTRAINT chk_payslips_incident_deduction
    CHECK (incident_deduction_amount >= 0);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    related_trip_id UUID,
    operational_week INTEGER NOT NULL,
    incident_type VARCHAR(30) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    remaining_amount NUMERIC(14, 2) NOT NULL,
    route_label VARCHAR(500) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    charge_method VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_incidents_career
        FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT fk_incidents_related_trip
        FOREIGN KEY (related_trip_id) REFERENCES trips (id) ON DELETE SET NULL,
    CONSTRAINT chk_incidents_operational_week CHECK (operational_week > 0),
    CONSTRAINT chk_incidents_type CHECK (
        incident_type IN ('INFRACTION', 'ACCIDENT', 'TOLL_CHARGE', 'OTHER')
    ),
    CONSTRAINT chk_incidents_amount CHECK (amount > 0),
    CONSTRAINT chk_incidents_remaining CHECK (
        remaining_amount >= 0 AND remaining_amount <= amount
    ),
    CONSTRAINT chk_incidents_route CHECK (BTRIM(route_label) <> ''),
    CONSTRAINT chk_incidents_description CHECK (BTRIM(description) <> ''),
    CONSTRAINT chk_incidents_charge_method CHECK (
        charge_method IN ('BALANCE', 'PAYSLIP')
    ),
    CONSTRAINT chk_incidents_status CHECK (
        status IN (
            'PAID_BALANCE',
            'PENDING_PAYSLIP',
            'PARTIALLY_DEDUCTED',
            'DEDUCTED_PAYSLIP',
            'CANCELLED'
        )
    ),
    CONSTRAINT chk_incidents_charge_state CHECK (
        (
            charge_method = 'BALANCE'
            AND status = 'PAID_BALANCE'
            AND remaining_amount = 0
        )
        OR
        (
            charge_method = 'PAYSLIP'
            AND status IN (
                'PENDING_PAYSLIP',
                'PARTIALLY_DEDUCTED',
                'DEDUCTED_PAYSLIP',
                'CANCELLED'
            )
        )
    ),
    CONSTRAINT chk_incidents_pending_state CHECK (
        status <> 'PENDING_PAYSLIP' OR remaining_amount = amount
    ),
    CONSTRAINT chk_incidents_partial_state CHECK (
        status <> 'PARTIALLY_DEDUCTED'
        OR (remaining_amount > 0 AND remaining_amount < amount)
    ),
    CONSTRAINT chk_incidents_closed_state CHECK (
        status NOT IN ('PAID_BALANCE', 'DEDUCTED_PAYSLIP', 'CANCELLED')
        OR remaining_amount = 0
    ),
    CONSTRAINT chk_incidents_updated_at CHECK (updated_at >= recorded_at)
);

CREATE INDEX idx_incidents_career_recorded_at
    ON incidents (career_id, recorded_at DESC, id DESC);

CREATE INDEX idx_incidents_pending_payslip
    ON incidents (career_id, operational_week, recorded_at, id)
    WHERE charge_method = 'PAYSLIP' AND remaining_amount > 0;

CREATE TABLE incident_payslip_deductions (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    payslip_id UUID NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_incident_deductions_incident
        FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_deductions_payslip
        FOREIGN KEY (payslip_id) REFERENCES payslips (id) ON DELETE CASCADE,
    CONSTRAINT chk_incident_deductions_amount CHECK (amount > 0),
    CONSTRAINT uq_incident_deductions_incident_payslip UNIQUE (incident_id, payslip_id)
);

CREATE INDEX idx_incident_deductions_payslip
    ON incident_payslip_deductions (payslip_id, recorded_at, id);
