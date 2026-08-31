ALTER TABLE careers
    ADD COLUMN payroll_level1_gross_override NUMERIC(14,2),
    ADD COLUMN payroll_route_overrun_rate_override NUMERIC(14,2),
    ADD COLUMN payroll_benefits_override NUMERIC(14,2),
    ADD COLUMN payroll_per_diem_rate_override NUMERIC(14,2);

ALTER TABLE careers
    ADD CONSTRAINT chk_careers_payroll_level1_gross_override
        CHECK (payroll_level1_gross_override IS NULL OR payroll_level1_gross_override >= 0),
    ADD CONSTRAINT chk_careers_payroll_route_overrun_rate_override
        CHECK (payroll_route_overrun_rate_override IS NULL OR payroll_route_overrun_rate_override >= 0),
    ADD CONSTRAINT chk_careers_payroll_benefits_override
        CHECK (payroll_benefits_override IS NULL OR payroll_benefits_override >= 0),
    ADD CONSTRAINT chk_careers_payroll_per_diem_rate_override
        CHECK (payroll_per_diem_rate_override IS NULL OR payroll_per_diem_rate_override >= 0);
