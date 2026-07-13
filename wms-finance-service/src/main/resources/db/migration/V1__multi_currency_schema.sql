-- =============================================================================
-- V1 — Çoklu Para Birimi Yönetimi (Prompt 8.1)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS finance;

-- ── currencies ───────────────────────────────────────────────────────────────
CREATE TABLE finance.currencies (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(3)      NOT NULL,
    symbol          VARCHAR(10)     NOT NULL,
    decimal_places  INTEGER         NOT NULL DEFAULT 2,
    name            VARCHAR(100)    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_currencies PRIMARY KEY (id),
    CONSTRAINT uk_currencies_code UNIQUE (code)
);

-- ── system_config ────────────────────────────────────────────────────────────
CREATE TABLE finance.system_config (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    default_currency_id UUID NOT NULL,

    CONSTRAINT pk_system_config PRIMARY KEY (id),
    CONSTRAINT fk_system_config_currency
        FOREIGN KEY (default_currency_id) REFERENCES finance.currencies (id)
);

-- ── company / location / customer currency settings (cross-service UUID refs) ──
CREATE TABLE finance.company_currency_settings (
    company_id        UUID NOT NULL,
    base_currency_id  UUID NOT NULL,

    CONSTRAINT pk_company_currency_settings PRIMARY KEY (company_id),
    CONSTRAINT fk_ccs_base_currency
        FOREIGN KEY (base_currency_id) REFERENCES finance.currencies (id)
);

CREATE TABLE finance.location_currency_settings (
    location_id       UUID NOT NULL,
    company_id        UUID NOT NULL,
    local_currency_id UUID NOT NULL,

    CONSTRAINT pk_location_currency_settings PRIMARY KEY (location_id),
    CONSTRAINT fk_lcs_local_currency
        FOREIGN KEY (local_currency_id) REFERENCES finance.currencies (id)
);

CREATE TABLE finance.customers (
    id                    UUID NOT NULL DEFAULT gen_random_uuid(),
    default_currency_id   UUID,

    CONSTRAINT pk_finance_customers PRIMARY KEY (id),
    CONSTRAINT fk_customers_default_currency
        FOREIGN KEY (default_currency_id) REFERENCES finance.currencies (id)
);

-- ── contracts ────────────────────────────────────────────────────────────────
CREATE TABLE finance.contracts (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    customer_id   UUID         NOT NULL,
    currency_id   UUID         NOT NULL,
    contract_code VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_contracts PRIMARY KEY (id),
    CONSTRAINT uk_contracts_code UNIQUE (contract_code),
    CONSTRAINT fk_contracts_customer
        FOREIGN KEY (customer_id) REFERENCES finance.customers (id),
    CONSTRAINT fk_contracts_currency
        FOREIGN KEY (currency_id) REFERENCES finance.currencies (id)
);

-- ── exchange_rates ───────────────────────────────────────────────────────────
CREATE TABLE finance.exchange_rates (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    source_currency_id  UUID            NOT NULL,
    target_currency_id  UUID            NOT NULL,
    rate_date           DATE            NOT NULL,
    rate_type           VARCHAR(30)     NOT NULL,
    rate                NUMERIC(18, 6)  NOT NULL,
    rate_source         VARCHAR(20),

    CONSTRAINT pk_exchange_rates PRIMARY KEY (id),
    CONSTRAINT fk_er_source_currency
        FOREIGN KEY (source_currency_id) REFERENCES finance.currencies (id),
    CONSTRAINT fk_er_target_currency
        FOREIGN KEY (target_currency_id) REFERENCES finance.currencies (id),
    CONSTRAINT uk_exchange_rate UNIQUE (source_currency_id, target_currency_id, rate_date, rate_type)
);

CREATE INDEX idx_exchange_rates_lookup
    ON finance.exchange_rates (source_currency_id, target_currency_id, rate_type, rate_date DESC);

-- ── financial_transactions ───────────────────────────────────────────────────
CREATE TABLE finance.financial_transactions (
    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),
    company_id            UUID            NOT NULL,
    location_id           UUID            NOT NULL,
    contract_id           UUID,
    original_currency_id  UUID            NOT NULL,
    original_amount       NUMERIC(18, 4)  NOT NULL,
    exchange_rate         NUMERIC(18, 6)  NOT NULL,
    base_currency_id      UUID            NOT NULL,
    converted_amount      NUMERIC(18, 4)  NOT NULL,
    transaction_date      TIMESTAMPTZ     NOT NULL,
    fallback_rate_used    BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_financial_transactions PRIMARY KEY (id),
    CONSTRAINT fk_ft_contract
        FOREIGN KEY (contract_id) REFERENCES finance.contracts (id),
    CONSTRAINT fk_ft_original_currency
        FOREIGN KEY (original_currency_id) REFERENCES finance.currencies (id),
    CONSTRAINT fk_ft_base_currency
        FOREIGN KEY (base_currency_id) REFERENCES finance.currencies (id)
);

CREATE INDEX idx_ft_company ON finance.financial_transactions (company_id);
CREATE INDEX idx_ft_location ON finance.financial_transactions (location_id);
CREATE INDEX idx_ft_transaction_date ON finance.financial_transactions (transaction_date);
