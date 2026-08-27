CREATE TABLE payslips (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    game_id VARCHAR(10) NOT NULL,
    operational_week INTEGER,
    payroll_month INTEGER,
    start_operational_week INTEGER NOT NULL,
    end_operational_week INTEGER NOT NULL,
    level SMALLINT NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    gross_amount NUMERIC(14,2) NOT NULL,
    tax_amount NUMERIC(14,2) NOT NULL,
    benefits_amount NUMERIC(14,2) NOT NULL,
    per_diem_amount NUMERIC(14,2) NOT NULL,
    net_salary_amount NUMERIC(14,2) NOT NULL,
    deposit_amount NUMERIC(14,2) NOT NULL,
    total_distance NUMERIC(14,2) NOT NULL,
    elapsed_minutes INTEGER NOT NULL,
    break_minutes INTEGER NOT NULL,
    worked_minutes INTEGER NOT NULL,
    overrun_minutes INTEGER NOT NULL,
    context_snapshot_json TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_payslips_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_payslips_game CHECK (game_id IN ('ATS', 'ETS2')),
    CONSTRAINT chk_payslips_period_context CHECK (
        (game_id = 'ATS' AND operational_week IS NOT NULL AND payroll_month IS NULL
            AND start_operational_week = operational_week AND end_operational_week = operational_week)
        OR
        (game_id = 'ETS2' AND operational_week IS NULL AND payroll_month IS NOT NULL)
    ),
    CONSTRAINT chk_payslips_operational_week CHECK (operational_week IS NULL OR operational_week >= 1),
    CONSTRAINT chk_payslips_payroll_month CHECK (payroll_month IS NULL OR payroll_month >= 1),
    CONSTRAINT chk_payslips_week_range CHECK (start_operational_week >= 1 AND end_operational_week >= start_operational_week),
    CONSTRAINT chk_payslips_level CHECK (level BETWEEN 1 AND 3),
    CONSTRAINT chk_payslips_display_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payslips_nonnegative_amounts CHECK (
        gross_amount >= 0 AND tax_amount >= 0 AND benefits_amount >= 0 AND per_diem_amount >= 0
        AND deposit_amount >= 0 AND total_distance >= 0
    ),
    CONSTRAINT chk_payslips_minutes CHECK (
        elapsed_minutes >= 0 AND break_minutes >= 0 AND worked_minutes >= 0 AND overrun_minutes >= 0
        AND break_minutes <= elapsed_minutes AND worked_minutes <= elapsed_minutes
    )
);

CREATE UNIQUE INDEX uq_payslips_ats_week ON payslips (career_id, operational_week) WHERE game_id = 'ATS';
CREATE UNIQUE INDEX uq_payslips_ets2_month ON payslips (career_id, payroll_month) WHERE game_id = 'ETS2';
CREATE INDEX idx_payslips_career_generated_at ON payslips (career_id, generated_at DESC, id DESC);

CREATE TABLE payslip_lines (
    id UUID PRIMARY KEY,
    payslip_id UUID NOT NULL,
    line_order INTEGER NOT NULL,
    code VARCHAR(60) NOT NULL,
    label VARCHAR(160) NOT NULL,
    line_type VARCHAR(20) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    quantity NUMERIC(16,4),
    rate NUMERIC(16,4),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    CONSTRAINT fk_payslip_lines_payslip FOREIGN KEY (payslip_id) REFERENCES payslips (id) ON DELETE CASCADE,
    CONSTRAINT chk_payslip_lines_order CHECK (line_order >= 1),
    CONSTRAINT chk_payslip_lines_code CHECK (BTRIM(code) <> ''),
    CONSTRAINT chk_payslip_lines_label CHECK (BTRIM(label) <> ''),
    CONSTRAINT chk_payslip_lines_type CHECK (line_type IN ('EARNING', 'DEDUCTION')),
    CONSTRAINT chk_payslip_lines_amount CHECK (amount >= 0),
    CONSTRAINT chk_payslip_lines_quantity CHECK (quantity IS NULL OR quantity >= 0),
    CONSTRAINT chk_payslip_lines_rate CHECK (rate IS NULL OR rate >= 0),
    CONSTRAINT uq_payslip_lines_order UNIQUE (payslip_id, line_order)
);
CREATE INDEX idx_payslip_lines_payslip_order ON payslip_lines (payslip_id, line_order);

ALTER TABLE payroll_periods ADD COLUMN payslip_id UUID;
ALTER TABLE payroll_periods ADD CONSTRAINT fk_payroll_periods_payslip
    FOREIGN KEY (payslip_id) REFERENCES payslips (id) ON DELETE SET NULL;
CREATE INDEX idx_payroll_periods_payslip_id ON payroll_periods (payslip_id);
