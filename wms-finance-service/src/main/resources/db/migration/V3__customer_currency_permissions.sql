-- =============================================================================
-- V3 — Müşteri Bazlı Para Birimi Tanımları (Prompt 9.1)
-- =============================================================================

-- ── customers tablosuna yeni kolonlar ────────────────────────────────────────
ALTER TABLE finance.customers
    ADD COLUMN name                    VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN invoicing_currency_id   UUID,
    ADD COLUMN rate_type               VARCHAR(30)  NOT NULL DEFAULT 'BUYING',
    ADD COLUMN rate_source             VARCHAR(20)  NOT NULL DEFAULT 'TCMB',
    ADD COLUMN exchange_diff_preference VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN is_active               BOOLEAN      NOT NULL DEFAULT TRUE;

ALTER TABLE finance.customers
    ALTER COLUMN name DROP DEFAULT;

ALTER TABLE finance.customers
    ADD CONSTRAINT fk_customers_invoicing_currency
        FOREIGN KEY (invoicing_currency_id) REFERENCES finance.currencies (id);

-- ── customer_permitted_currencies ────────────────────────────────────────────
CREATE TABLE finance.customer_permitted_currencies (
    id          UUID NOT NULL DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    currency_id UUID NOT NULL,

    CONSTRAINT pk_customer_permitted_currencies PRIMARY KEY (id),
    CONSTRAINT uk_cpc_customer_currency UNIQUE (customer_id, currency_id),
    CONSTRAINT fk_cpc_customer
        FOREIGN KEY (customer_id) REFERENCES finance.customers (id),
    CONSTRAINT fk_cpc_currency
        FOREIGN KEY (currency_id) REFERENCES finance.currencies (id)
);

CREATE INDEX idx_cpc_customer ON finance.customer_permitted_currencies (customer_id);

-- ── contracts tablosuna tarih kolonları ──────────────────────────────────────
ALTER TABLE finance.contracts
    ADD COLUMN start_date TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN end_date   TIMESTAMP;

ALTER TABLE finance.contracts
    ALTER COLUMN start_date DROP DEFAULT;
