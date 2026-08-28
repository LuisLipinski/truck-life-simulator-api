CREATE TABLE monthly_expenses (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    expense_type VARCHAR(10) NOT NULL,
    category VARCHAR(40),
    name VARCHAR(120) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    included BOOLEAN NOT NULL DEFAULT TRUE,
    display_currency VARCHAR(3) NOT NULL,
    policy_version VARCHAR(60) NOT NULL,
    context_snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_monthly_expenses_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_monthly_expenses_type CHECK (expense_type IN ('STANDARD','CUSTOM')),
    CONSTRAINT chk_monthly_expenses_category CHECK (
        (expense_type='STANDARD' AND category IN ('RENT','ELECTRICITY','WATER','INTERNET','PHONE','GROCERIES','EATING_OUT','HEALTH','PUBLIC_TRANSPORT','HOUSEHOLD','LEISURE'))
        OR (expense_type='CUSTOM' AND category IS NULL)
    ),
    CONSTRAINT chk_monthly_expenses_name CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_monthly_expenses_amount CHECK (amount >= 0),
    CONSTRAINT chk_monthly_expenses_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_monthly_expenses_snapshot CHECK (BTRIM(context_snapshot_json) <> '' AND jsonb_typeof(context_snapshot_json::jsonb)='object'),
    CONSTRAINT uq_monthly_expenses_career_category UNIQUE (career_id, category)
);
CREATE INDEX idx_monthly_expenses_career_type ON monthly_expenses (career_id, expense_type, created_at, id);

CREATE TABLE monthly_expense_applications (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    operational_week INTEGER NOT NULL,
    payroll_month INTEGER,
    amount NUMERIC(14,2) NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    context_snapshot_json TEXT NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_monthly_expense_applications_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_monthly_expense_applications_week CHECK (operational_week > 0),
    CONSTRAINT chk_monthly_expense_applications_month CHECK (payroll_month IS NULL OR payroll_month > 0),
    CONSTRAINT chk_monthly_expense_applications_amount CHECK (amount >= 0),
    CONSTRAINT chk_monthly_expense_applications_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_monthly_expense_applications_snapshot CHECK (BTRIM(context_snapshot_json) <> '' AND jsonb_typeof(context_snapshot_json::jsonb)='object')
);
CREATE INDEX idx_monthly_expense_applications_career_applied ON monthly_expense_applications (career_id, applied_at, id);

CREATE TABLE emergency_reserve (
    career_id UUID PRIMARY KEY,
    balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    annual_yield_rate NUMERIC(8,6) NOT NULL DEFAULT 0.032500,
    auto_contribution_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_contribution_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    display_currency VARCHAR(3) NOT NULL,
    policy_version VARCHAR(60) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_emergency_reserve_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_emergency_reserve_balance CHECK (balance >= 0),
    CONSTRAINT chk_emergency_reserve_yield CHECK (annual_yield_rate >= 0 AND annual_yield_rate <= 1),
    CONSTRAINT chk_emergency_reserve_auto_amount CHECK (auto_contribution_amount >= 0),
    CONSTRAINT chk_emergency_reserve_currency CHECK (display_currency ~ '^[A-Z]{3}$')
);

CREATE TABLE emergency_reserve_events (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    payslip_id UUID,
    event_type VARCHAR(30) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    balance_before NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    operational_week INTEGER NOT NULL,
    payroll_month INTEGER,
    reason VARCHAR(240),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_emergency_reserve_events_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT fk_emergency_reserve_events_payslip FOREIGN KEY (payslip_id) REFERENCES payslips (id) ON DELETE SET NULL,
    CONSTRAINT chk_emergency_reserve_events_type CHECK (event_type IN ('MANUAL_DEPOSIT','MANUAL_WITHDRAWAL','AUTO_CONTRIBUTION','INTEREST')),
    CONSTRAINT chk_emergency_reserve_events_amount CHECK (amount > 0),
    CONSTRAINT chk_emergency_reserve_events_balances CHECK (balance_before >= 0 AND balance_after >= 0),
    CONSTRAINT chk_emergency_reserve_events_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_emergency_reserve_events_week CHECK (operational_week > 0),
    CONSTRAINT chk_emergency_reserve_events_month CHECK (payroll_month IS NULL OR payroll_month > 0),
    CONSTRAINT chk_emergency_reserve_withdrawal_reason CHECK (event_type <> 'MANUAL_WITHDRAWAL' OR (reason IS NOT NULL AND BTRIM(reason) <> '')),
    CONSTRAINT uq_emergency_reserve_payslip_event UNIQUE (payslip_id, event_type)
);
CREATE INDEX idx_emergency_reserve_events_career_recorded ON emergency_reserve_events (career_id, recorded_at, id);

ALTER TABLE payslips
    ADD COLUMN reserve_interest_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN reserve_contribution_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN balance_credit_amount NUMERIC(14,2);
UPDATE payslips SET balance_credit_amount = deposit_amount;
ALTER TABLE payslips ALTER COLUMN balance_credit_amount SET NOT NULL;
ALTER TABLE payslips
    ADD CONSTRAINT chk_payslips_reserve_interest CHECK (reserve_interest_amount >= 0),
    ADD CONSTRAINT chk_payslips_reserve_contribution CHECK (reserve_contribution_amount >= 0 AND reserve_contribution_amount <= deposit_amount),
    ADD CONSTRAINT chk_payslips_balance_credit CHECK (balance_credit_amount >= 0 AND balance_credit_amount = deposit_amount - reserve_contribution_amount);
