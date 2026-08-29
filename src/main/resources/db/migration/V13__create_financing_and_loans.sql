CREATE TABLE financial_contracts (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    origination_operation_id UUID NOT NULL UNIQUE,
    product_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    policy_version VARCHAR(100) NOT NULL,
    policy_source VARCHAR(500) NOT NULL,
    policy_reference_as_of DATE NOT NULL,
    rate_basis VARCHAR(80) NOT NULL,
    jurisdiction_country_code VARCHAR(10),
    jurisdiction_state_code VARCHAR(10),
    jurisdiction_city VARCHAR(160) NOT NULL,
    display_currency VARCHAR(3) NOT NULL,
    requested_amount NUMERIC(14,2) NOT NULL,
    principal NUMERIC(14,2) NOT NULL,
    down_payment NUMERIC(14,2) NOT NULL,
    annual_interest_rate NUMERIC(12,10) NOT NULL,
    amortization_method VARCHAR(50) NOT NULL,
    payment_frequency VARCHAR(20) NOT NULL,
    term_periods INTEGER NOT NULL,
    current_schedule_version INTEGER NOT NULL DEFAULT 1,
    expected_total_cost NUMERIC(14,2) NOT NULL,
    remaining_principal NUMERIC(14,2) NOT NULL,
    prepayment_fee_rate NUMERIC(12,10) NOT NULL DEFAULT 0,
    late_fee_rate NUMERIC(12,10) NOT NULL DEFAULT 0,
    max_missed_installments INTEGER NOT NULL DEFAULT 3,
    originated_operational_week INTEGER NOT NULL,
    originated_payroll_month INTEGER,
    policy_snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_financial_contracts_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_financial_contracts_product CHECK (product_type IN ('VEHICLE_FINANCING','PERSONAL_LOAN')),
    CONSTRAINT chk_financial_contracts_status CHECK (status IN ('ACTIVE','DELINQUENT','DEFAULTED','PAID_OFF')),
    CONSTRAINT chk_financial_contracts_amounts CHECK (requested_amount > 0 AND principal > 0 AND down_payment >= 0 AND remaining_principal >= 0 AND remaining_principal <= principal AND expected_total_cost >= principal + down_payment),
    CONSTRAINT chk_financial_contracts_rate CHECK (annual_interest_rate >= 0 AND annual_interest_rate < 1 AND prepayment_fee_rate >= 0 AND late_fee_rate >= 0),
    CONSTRAINT chk_financial_contracts_terms CHECK (term_periods > 0 AND current_schedule_version > 0 AND max_missed_installments > 0),
    CONSTRAINT chk_financial_contracts_frequency CHECK (payment_frequency IN ('WEEKLY','BIWEEKLY','MONTHLY')),
    CONSTRAINT chk_financial_contracts_amortization CHECK (amortization_method='FIXED_PAYMENT_REDUCING_BALANCE'),
    CONSTRAINT chk_financial_contracts_week CHECK (originated_operational_week > 0),
    CONSTRAINT chk_financial_contracts_month CHECK (originated_payroll_month IS NULL OR originated_payroll_month > 0),
    CONSTRAINT chk_financial_contracts_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_financial_contracts_snapshot CHECK (BTRIM(policy_snapshot_json) <> '' AND jsonb_typeof(policy_snapshot_json::jsonb)='object')
);

CREATE TABLE financial_installments (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL,
    schedule_version INTEGER NOT NULL,
    installment_number INTEGER NOT NULL,
    due_operational_week INTEGER,
    due_payroll_month INTEGER,
    scheduled_amount NUMERIC(14,2) NOT NULL,
    principal_amount NUMERIC(14,2) NOT NULL,
    interest_amount NUMERIC(14,2) NOT NULL,
    fee_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    principal_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    interest_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    fee_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_installments_contract FOREIGN KEY (contract_id) REFERENCES financial_contracts (id) ON DELETE CASCADE,
    CONSTRAINT uq_financial_installments_schedule UNIQUE (contract_id,schedule_version,installment_number),
    CONSTRAINT chk_financial_installments_schedule CHECK (schedule_version > 0 AND installment_number > 0),
    CONSTRAINT chk_financial_installments_due CHECK ((due_operational_week IS NOT NULL) <> (due_payroll_month IS NOT NULL) AND (due_operational_week IS NULL OR due_operational_week > 0) AND (due_payroll_month IS NULL OR due_payroll_month > 0)),
    CONSTRAINT chk_financial_installments_amounts CHECK (scheduled_amount = principal_amount + interest_amount + fee_amount AND principal_amount >= 0 AND interest_amount >= 0 AND fee_amount >= 0 AND paid_amount >= 0 AND paid_amount <= scheduled_amount),
    CONSTRAINT chk_financial_installments_paid_parts CHECK (paid_amount = principal_paid + interest_paid + fee_paid AND principal_paid >= 0 AND principal_paid <= principal_amount AND interest_paid >= 0 AND interest_paid <= interest_amount AND fee_paid >= 0 AND fee_paid <= fee_amount),
    CONSTRAINT chk_financial_installments_status CHECK (status IN ('SCHEDULED','PARTIALLY_PAID','PAID','OVERDUE','SUPERSEDED'))
);

CREATE TABLE financial_payments (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL,
    operation_id UUID NOT NULL UNIQUE,
    payment_type VARCHAR(30) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    principal_amount NUMERIC(14,2) NOT NULL,
    interest_amount NUMERIC(14,2) NOT NULL,
    fee_amount NUMERIC(14,2) NOT NULL,
    balance_before NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    operational_week INTEGER NOT NULL,
    payroll_month INTEGER,
    display_currency VARCHAR(3) NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_payments_contract FOREIGN KEY (contract_id) REFERENCES financial_contracts (id) ON DELETE CASCADE,
    CONSTRAINT chk_financial_payments_type CHECK (payment_type IN ('REGULAR','AUTO','EXTRA_PRINCIPAL','PAYOFF')),
    CONSTRAINT chk_financial_payments_amounts CHECK (amount > 0 AND principal_amount >= 0 AND interest_amount >= 0 AND fee_amount >= 0 AND amount = principal_amount + interest_amount + fee_amount),
    CONSTRAINT chk_financial_payments_balance CHECK (balance_after = balance_before - amount),
    CONSTRAINT chk_financial_payments_week CHECK (operational_week > 0),
    CONSTRAINT chk_financial_payments_month CHECK (payroll_month IS NULL OR payroll_month > 0),
    CONSTRAINT chk_financial_payments_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_financial_payments_metadata CHECK (BTRIM(metadata_json) <> '' AND jsonb_typeof(metadata_json::jsonb)='object')
);

CREATE TABLE financial_contract_events (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    operational_week INTEGER NOT NULL,
    payroll_month INTEGER,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_financial_contract_events_contract FOREIGN KEY (contract_id) REFERENCES financial_contracts (id) ON DELETE CASCADE,
    CONSTRAINT chk_financial_contract_events_type CHECK (event_type IN ('ORIGINATED','DELINQUENT','DEFAULTED','RESCHEDULED','PAID_OFF')),
    CONSTRAINT chk_financial_contract_events_week CHECK (operational_week > 0),
    CONSTRAINT chk_financial_contract_events_month CHECK (payroll_month IS NULL OR payroll_month > 0),
    CONSTRAINT chk_financial_contract_events_metadata CHECK (BTRIM(metadata_json) <> '' AND jsonb_typeof(metadata_json::jsonb)='object')
);

CREATE INDEX idx_financial_contracts_career_status ON financial_contracts (career_id,status,created_at DESC,id DESC);
CREATE INDEX idx_financial_installments_contract_due ON financial_installments (contract_id,schedule_version,due_operational_week,due_payroll_month,installment_number);
CREATE INDEX idx_financial_payments_contract_recorded ON financial_payments (contract_id,recorded_at,id);
CREATE INDEX idx_financial_contract_events_contract_recorded ON financial_contract_events (contract_id,recorded_at,id);

ALTER TABLE ledger_entries DROP CONSTRAINT chk_ledger_entries_type;
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_entries_type CHECK (entry_type IN (
    'OPENING_BALANCE','PAYSLIP_CREDIT','MONTHLY_EXPENSE','INCIDENT_CHARGE','ACADEMY_FEE','QUALIFICATION_FEE',
    'RESERVE_DEPOSIT','RESERVE_WITHDRAWAL','RESERVE_AUTO_CONTRIBUTION','RESERVE_INTEREST','BALANCE_ADJUSTMENT',
    'LOAN_DISBURSEMENT','FINANCING_DOWN_PAYMENT','DEBT_PAYMENT'
));

ALTER TABLE ledger_entries DROP CONSTRAINT chk_ledger_entries_source;
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_entries_source CHECK (source_type IN (
    'CAREER','PAYSLIP','MONTHLY_EXPENSE_APPLICATION','INCIDENT','ACADEMY_PROGRESS','QUALIFICATION',
    'EMERGENCY_RESERVE_EVENT','BALANCE_ADJUSTMENT','FINANCIAL_CONTRACT','FINANCIAL_PAYMENT'
));
