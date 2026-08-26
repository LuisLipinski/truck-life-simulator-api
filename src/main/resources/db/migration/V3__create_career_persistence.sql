CREATE TABLE careers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    game_id VARCHAR(10) NOT NULL,
    driver_name VARCHAR(120) NOT NULL,
    company_name VARCHAR(160),
    biography TEXT,
    current_level SMALLINT NOT NULL DEFAULT 1,
    balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    base_currency CHAR(3) NOT NULL,
    display_currency CHAR(3) NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1,
    exchange_rate_as_of DATE,
    state_code VARCHAR(10),
    country_code VARCHAR(10),
    base_city VARCHAR(160) NOT NULL,
    default_truck_make VARCHAR(80),
    default_truck_model VARCHAR(120),
    city_market_version VARCHAR(40),
    city_market_label VARCHAR(100),
    city_cost_factor NUMERIC(8,4),
    city_salary_factor NUMERIC(8,4),
    current_operational_week INTEGER NOT NULL DEFAULT 1,
    current_payroll_month INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_careers_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_careers_game CHECK (game_id IN ('ATS', 'ETS2')),
    CONSTRAINT chk_careers_driver_name CHECK (CHAR_LENGTH(BTRIM(driver_name)) BETWEEN 1 AND 120),
    CONSTRAINT chk_careers_company_name CHECK (company_name IS NULL OR BTRIM(company_name) <> ''),
    CONSTRAINT chk_careers_level CHECK (current_level BETWEEN 1 AND 3),
    CONSTRAINT chk_careers_base_currency CHECK (base_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_careers_display_currency CHECK (display_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_careers_exchange_rate CHECK (exchange_rate > 0),
    CONSTRAINT chk_careers_base_city CHECK (BTRIM(base_city) <> ''),
    CONSTRAINT chk_careers_default_truck_make CHECK (
        default_truck_make IS NULL OR BTRIM(default_truck_make) <> ''
    ),
    CONSTRAINT chk_careers_default_truck_model CHECK (
        default_truck_model IS NULL OR BTRIM(default_truck_model) <> ''
    ),
    CONSTRAINT chk_careers_game_location CHECK (
        (game_id = 'ATS' AND state_code IS NOT NULL AND BTRIM(state_code) <> '')
        OR
        (game_id = 'ETS2' AND country_code IS NOT NULL AND BTRIM(country_code) <> '')
    ),
    CONSTRAINT chk_careers_city_cost_factor CHECK (city_cost_factor IS NULL OR city_cost_factor > 0),
    CONSTRAINT chk_careers_city_salary_factor CHECK (city_salary_factor IS NULL OR city_salary_factor > 0),
    CONSTRAINT chk_careers_operational_week CHECK (current_operational_week >= 1),
    CONSTRAINT chk_careers_payroll_month CHECK (
        current_payroll_month IS NULL OR current_payroll_month BETWEEN 1 AND 12
    ),
    CONSTRAINT chk_careers_updated_at CHECK (updated_at >= created_at),
    CONSTRAINT chk_careers_version CHECK (version >= 0)
);

CREATE INDEX idx_careers_user_game_created_at
    ON careers (user_id, game_id, created_at, id);
CREATE INDEX idx_careers_updated_at ON careers (updated_at);
