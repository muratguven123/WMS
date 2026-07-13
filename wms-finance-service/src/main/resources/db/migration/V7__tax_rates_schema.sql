-- =============================================================================
-- V7 — Vergi Tipleri, Tarih-Versiyonlamalı Oranlar, Audit Log (Prompt 14.1)
-- =============================================================================

CREATE TABLE finance.tax_types (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_tax_types      PRIMARY KEY (id),
    CONSTRAINT uq_tax_type_code UNIQUE (code)
);

CREATE TABLE finance.tax_rates (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    tax_type_id    UUID          NOT NULL,
    country_id     UUID          NOT NULL,
    location_id    UUID,
    customer_id    UUID,
    product_type   VARCHAR(50),
    operation_type VARCHAR(50),
    rate           NUMERIC(5, 2) NOT NULL,
    start_date     DATE          NOT NULL,
    end_date       DATE,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tax_rates          PRIMARY KEY (id),
    CONSTRAINT fk_tax_rate_tax_type  FOREIGN KEY (tax_type_id)
        REFERENCES finance.tax_types (id),
    CONSTRAINT chk_tax_rate_date_order CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_tax_rate_positive   CHECK (rate >= 0)
);

CREATE INDEX idx_tax_rate_tax_type   ON finance.tax_rates (tax_type_id);
CREATE INDEX idx_tax_rate_country    ON finance.tax_rates (country_id);
CREATE INDEX idx_tax_rate_date_range ON finance.tax_rates (start_date, end_date);
CREATE INDEX idx_tax_rate_active     ON finance.tax_rates (is_active) WHERE is_active = TRUE;

CREATE UNIQUE INDEX uq_tax_rate_open_active
    ON finance.tax_rates (tax_type_id, country_id,
        COALESCE(location_id,  '00000000-0000-0000-0000-000000000000'),
        COALESCE(customer_id,  '00000000-0000-0000-0000-000000000000'),
        COALESCE(product_type,  ''),
        COALESCE(operation_type, ''))
    WHERE end_date IS NULL AND is_active = TRUE;

CREATE TABLE finance.tax_rate_audit_logs (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    tax_rate_id UUID          NOT NULL,
    action_type VARCHAR(20)   NOT NULL,
    old_rate    NUMERIC(5, 2),
    new_rate    NUMERIC(5, 2) NOT NULL,
    user_id     UUID,
    changed_at  TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tax_rate_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_tral_tax_rate FOREIGN KEY (tax_rate_id)
        REFERENCES finance.tax_rates (id),
    CONSTRAINT chk_tral_action_type CHECK (action_type IN ('INSERT', 'UPDATE_RATE', 'EXPIRE'))
);

CREATE INDEX idx_tral_tax_rate   ON finance.tax_rate_audit_logs (tax_rate_id);
CREATE INDEX idx_tral_changed_at ON finance.tax_rate_audit_logs (changed_at DESC);
