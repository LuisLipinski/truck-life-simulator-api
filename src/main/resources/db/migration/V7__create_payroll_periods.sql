ALTER TABLE careers DROP CONSTRAINT chk_careers_payroll_month;

UPDATE careers
SET current_payroll_month = NULL
WHERE game_id = 'ATS';

UPDATE careers
SET current_payroll_month = 1
WHERE game_id = 'ETS2'
  AND current_payroll_month IS NULL;

ALTER TABLE careers
    ADD CONSTRAINT chk_careers_payroll_month_context CHECK (
        (game_id = 'ATS' AND current_payroll_month IS NULL)
        OR
        (game_id = 'ETS2' AND current_payroll_month >= 1)
    );

CREATE TABLE payroll_periods (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    operational_week INTEGER NOT NULL,
    payroll_month INTEGER,
    context_snapshot_json TEXT NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_payroll_periods_career
        FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_payroll_periods_operational_week CHECK (operational_week >= 1),
    CONSTRAINT chk_payroll_periods_payroll_month CHECK (
        payroll_month IS NULL OR payroll_month >= 1
    ),
    CONSTRAINT uq_payroll_periods_career_week UNIQUE (career_id, operational_week)
);

CREATE INDEX idx_payroll_periods_career_month_week
    ON payroll_periods (career_id, payroll_month, operational_week);
