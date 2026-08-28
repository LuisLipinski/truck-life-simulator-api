CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    entry_type VARCHAR(40) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    entry_order SMALLINT NOT NULL DEFAULT 10,
    operational_week INTEGER NOT NULL,
    payroll_month INTEGER,
    amount NUMERIC(14,2) NOT NULL,
    balance_delta NUMERIC(14,2) NOT NULL,
    reserve_delta NUMERIC(14,2) NOT NULL,
    balance_before NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    reserve_balance_before NUMERIC(14,2),
    reserve_balance_after NUMERIC(14,2),
    display_currency VARCHAR(3) NOT NULL,
    description VARCHAR(240) NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ledger_entries_career FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_ledger_entries_type CHECK (entry_type IN (
        'OPENING_BALANCE','PAYSLIP_CREDIT','MONTHLY_EXPENSE','INCIDENT_CHARGE','ACADEMY_FEE','QUALIFICATION_FEE',
        'RESERVE_DEPOSIT','RESERVE_WITHDRAWAL','RESERVE_AUTO_CONTRIBUTION','RESERVE_INTEREST','BALANCE_ADJUSTMENT'
    )),
    CONSTRAINT chk_ledger_entries_source CHECK (source_type IN (
        'CAREER','PAYSLIP','MONTHLY_EXPENSE_APPLICATION','INCIDENT','ACADEMY_PROGRESS','QUALIFICATION',
        'EMERGENCY_RESERVE_EVENT','BALANCE_ADJUSTMENT'
    )),
    CONSTRAINT chk_ledger_entries_order CHECK (entry_order >= 0),
    CONSTRAINT chk_ledger_entries_week CHECK (operational_week > 0),
    CONSTRAINT chk_ledger_entries_month CHECK (payroll_month IS NULL OR payroll_month > 0),
    CONSTRAINT chk_ledger_entries_balance CHECK (balance_after = balance_before + balance_delta),
    CONSTRAINT chk_ledger_entries_reserve_snapshot CHECK (
        (reserve_balance_before IS NULL AND reserve_balance_after IS NULL)
        OR (reserve_balance_before IS NOT NULL AND reserve_balance_after IS NOT NULL
            AND reserve_balance_before >= 0 AND reserve_balance_after >= 0
            AND reserve_balance_after = reserve_balance_before + reserve_delta)
    ),
    CONSTRAINT chk_ledger_entries_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_ledger_entries_description CHECK (BTRIM(description) <> ''),
    CONSTRAINT chk_ledger_entries_metadata CHECK (BTRIM(metadata_json) <> '' AND jsonb_typeof(metadata_json::jsonb)='object'),
    CONSTRAINT uq_ledger_entries_source UNIQUE (source_type, source_id, entry_type)
);

CREATE INDEX idx_ledger_entries_career_recorded ON ledger_entries (career_id, recorded_at DESC, entry_order DESC, id DESC);
CREATE INDEX idx_ledger_entries_career_type ON ledger_entries (career_id, entry_type, recorded_at DESC, id DESC);

WITH movement_entries AS (
    SELECT p.career_id, 'PAYSLIP_CREDIT'::text AS entry_type, 'PAYSLIP'::text AS source_type, p.id AS source_id,
           30::smallint AS entry_order, p.end_operational_week AS operational_week, p.payroll_month,
           p.balance_credit_amount AS amount, p.balance_credit_amount AS balance_delta,
           0.00::numeric(14,2) AS reserve_delta, NULL::numeric(14,2) AS reserve_balance_before,
           NULL::numeric(14,2) AS reserve_balance_after, p.display_currency,
           CASE WHEN p.game_id='ATS' THEN 'Holerite semanal — Semana ' || p.end_operational_week
                ELSE 'Holerite mensal — Mês ' || p.payroll_month END AS description,
           jsonb_build_object('backfilled',true,'depositAmount',p.deposit_amount,
                              'incidentDeductionAmount',p.incident_deduction_amount,
                              'reserveContributionAmount',p.reserve_contribution_amount)::text AS metadata_json,
           p.generated_at AS recorded_at
    FROM payslips p

    UNION ALL
    SELECT a.career_id, 'MONTHLY_EXPENSE', 'MONTHLY_EXPENSE_APPLICATION', a.id, 10::smallint,
           a.operational_week, a.payroll_month, -a.amount, -a.amount, 0.00::numeric(14,2), NULL, NULL,
           a.display_currency, 'Despesas mensais aplicadas', a.context_snapshot_json, a.applied_at
    FROM monthly_expense_applications a

    UNION ALL
    SELECT i.career_id, 'INCIDENT_CHARGE', 'INCIDENT', i.id, 10::smallint,
           i.operational_week, NULL::integer, -i.amount, -i.amount, 0.00::numeric(14,2), NULL, NULL,
           c.display_currency, LEFT('Ocorrência: ' || i.description,240),
           jsonb_build_object('backfilled',true,'incidentType',i.incident_type,'route',i.route_label,'chargeMethod',i.charge_method)::text,
           i.recorded_at
    FROM incidents i JOIN careers c ON c.id=i.career_id
    WHERE i.charge_method='BALANCE'

    UNION ALL
    SELECT a.career_id, 'ACADEMY_FEE', 'ACADEMY_PROGRESS', a.id, 10::smallint,
           a.operational_week, NULL::integer, -a.fee_amount, -a.fee_amount, 0.00::numeric(14,2), NULL, NULL,
           a.display_currency, LEFT('Driving Academy: ' || a.module_name,240), a.context_snapshot_json, a.completed_at
    FROM academy_progress a

    UNION ALL
    SELECT q.career_id, 'QUALIFICATION_FEE', 'QUALIFICATION', q.id, 10::smallint,
           q.operational_week, NULL::integer, -q.fee_amount, -q.fee_amount, 0.00::numeric(14,2), NULL, NULL,
           q.display_currency, LEFT('Qualificação: ' || q.qualification_name,240), q.context_snapshot_json, q.acquired_at
    FROM qualifications q

    UNION ALL
    SELECT e.career_id,
           CASE e.event_type WHEN 'MANUAL_DEPOSIT' THEN 'RESERVE_DEPOSIT'
                             WHEN 'MANUAL_WITHDRAWAL' THEN 'RESERVE_WITHDRAWAL'
                             WHEN 'AUTO_CONTRIBUTION' THEN 'RESERVE_AUTO_CONTRIBUTION'
                             ELSE 'RESERVE_INTEREST' END,
           'EMERGENCY_RESERVE_EVENT', e.id,
           CASE e.event_type WHEN 'INTEREST' THEN 10 WHEN 'AUTO_CONTRIBUTION' THEN 20 ELSE 10 END::smallint,
           e.operational_week, e.payroll_month,
           CASE e.event_type WHEN 'MANUAL_DEPOSIT' THEN -e.amount WHEN 'MANUAL_WITHDRAWAL' THEN e.amount ELSE e.amount END,
           CASE e.event_type WHEN 'MANUAL_DEPOSIT' THEN -e.amount WHEN 'MANUAL_WITHDRAWAL' THEN e.amount ELSE 0.00::numeric(14,2) END,
           CASE e.event_type WHEN 'MANUAL_WITHDRAWAL' THEN -e.amount ELSE e.amount END,
           e.balance_before, e.balance_after, e.display_currency,
           CASE e.event_type WHEN 'MANUAL_DEPOSIT' THEN 'Aporte manual à reserva de emergência'
                             WHEN 'MANUAL_WITHDRAWAL' THEN LEFT('Resgate da reserva — ' || e.reason,240)
                             WHEN 'AUTO_CONTRIBUTION' THEN 'Aporte automático à reserva — holerite'
                             ELSE 'Rendimento da reserva — holerite' END,
           jsonb_build_object('backfilled',true,'eventType',e.event_type,'payslipId',e.payslip_id,'reason',e.reason)::text,
           e.recorded_at
    FROM emergency_reserve_events e
),
movement_totals AS (
    SELECT career_id, COALESCE(SUM(balance_delta),0.00)::numeric(14,2) AS balance_delta_total
    FROM movement_entries GROUP BY career_id
),
opening_entries AS (
    SELECT c.id AS career_id, 'OPENING_BALANCE'::text AS entry_type, 'CAREER'::text AS source_type, c.id AS source_id,
           0::smallint AS entry_order, 1 AS operational_week,
           CASE WHEN c.game_id='ETS2' THEN 1 ELSE NULL END::integer AS payroll_month,
           (c.balance-COALESCE(t.balance_delta_total,0.00))::numeric(14,2) AS amount,
           (c.balance-COALESCE(t.balance_delta_total,0.00))::numeric(14,2) AS balance_delta,
           0.00::numeric(14,2) AS reserve_delta, NULL::numeric(14,2) AS reserve_balance_before,
           NULL::numeric(14,2) AS reserve_balance_after, c.display_currency,
           'Saldo inicial da carreira'::text AS description,
           jsonb_build_object('backfilled',true,'derivedOpeningBalance',true)::text AS metadata_json,
           c.created_at AS recorded_at
    FROM careers c LEFT JOIN movement_totals t ON t.career_id=c.id
),
all_entries AS (
    SELECT * FROM opening_entries
    UNION ALL
    SELECT * FROM movement_entries
),
ordered_entries AS (
    SELECT e.*,
           SUM(e.balance_delta) OVER (
               PARTITION BY e.career_id
               ORDER BY e.recorded_at,e.entry_order,e.source_type,e.source_id,e.entry_type
               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
           )::numeric(14,2) AS calculated_balance_after
    FROM all_entries e
)
INSERT INTO ledger_entries (
    id,career_id,entry_type,source_type,source_id,entry_order,operational_week,payroll_month,
    amount,balance_delta,reserve_delta,balance_before,balance_after,reserve_balance_before,reserve_balance_after,
    display_currency,description,metadata_json,recorded_at
)
SELECT md5(source_type || ':' || source_id::text || ':' || entry_type)::uuid,
       career_id,entry_type,source_type,source_id,entry_order,operational_week,payroll_month,
       amount,balance_delta,reserve_delta,(calculated_balance_after-balance_delta)::numeric(14,2),calculated_balance_after,
       reserve_balance_before,reserve_balance_after,display_currency,description,metadata_json,recorded_at
FROM ordered_entries;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM emergency_reserve r
        LEFT JOIN (
            SELECT career_id, COALESCE(SUM(reserve_delta),0.00)::numeric(14,2) AS reconstructed_balance
            FROM ledger_entries GROUP BY career_id
        ) l ON l.career_id=r.career_id
        WHERE COALESCE(l.reconstructed_balance,0.00)<>r.balance
    ) THEN
        RAISE EXCEPTION 'Financial ledger reserve backfill does not reconcile with emergency_reserve';
    END IF;
END $$;
