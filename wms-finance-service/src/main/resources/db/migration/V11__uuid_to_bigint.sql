-- =============================================================================
-- V11 — UUID → BIGINT migration (wms_finance_db)
-- Requires wms_core_db V16__uuid_to_bigint.sql to have run first.
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

-- ---------------------------------------------------------------------------
-- 1. id_legacy_map — core mappings via dblink + local entity mappings
-- ---------------------------------------------------------------------------
CREATE TABLE id_legacy_map (
    entity_type VARCHAR(50) NOT NULL,
    old_uuid    UUID        NOT NULL,
    new_id      BIGINT      NOT NULL,
    PRIMARY KEY (entity_type, old_uuid)
);

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT entity_type, old_uuid, new_id
FROM dblink(
    'host=localhost dbname=wms_core_db user=postgres password=postgres',
    'SELECT entity_type, old_uuid, new_id FROM id_legacy_map'
) AS t(entity_type VARCHAR(50), old_uuid UUID, new_id BIGINT);

CREATE OR REPLACE FUNCTION map_uuid(p_entity_type VARCHAR, p_uuid UUID)
RETURNS BIGINT
LANGUAGE sql
STABLE
AS $$
    SELECT m.new_id
    FROM id_legacy_map m
    WHERE m.entity_type = p_entity_type
      AND m.old_uuid = p_uuid;
$$;

-- ---------------------------------------------------------------------------
-- 2. Drop FK constraints (recreated after column swap)
-- ---------------------------------------------------------------------------
ALTER TABLE finance.system_config
    DROP CONSTRAINT IF EXISTS fk_system_config_currency;

ALTER TABLE finance.company_currency_settings
    DROP CONSTRAINT IF EXISTS fk_ccs_base_currency;

ALTER TABLE finance.location_currency_settings
    DROP CONSTRAINT IF EXISTS fk_lcs_local_currency;

ALTER TABLE finance.customers
    DROP CONSTRAINT IF EXISTS fk_customers_default_currency,
    DROP CONSTRAINT IF EXISTS fk_customers_invoicing_currency;

ALTER TABLE finance.contracts
    DROP CONSTRAINT IF EXISTS fk_contracts_customer,
    DROP CONSTRAINT IF EXISTS fk_contracts_currency;

ALTER TABLE finance.exchange_rates
    DROP CONSTRAINT IF EXISTS fk_er_source_currency,
    DROP CONSTRAINT IF EXISTS fk_er_target_currency;

ALTER TABLE finance.financial_transactions
    DROP CONSTRAINT IF EXISTS fk_ft_contract,
    DROP CONSTRAINT IF EXISTS fk_ft_original_currency,
    DROP CONSTRAINT IF EXISTS fk_ft_base_currency;

ALTER TABLE finance.customer_permitted_currencies
    DROP CONSTRAINT IF EXISTS fk_cpc_customer,
    DROP CONSTRAINT IF EXISTS fk_cpc_currency;

ALTER TABLE finance.exchange_rate_audit_logs
    DROP CONSTRAINT IF EXISTS fk_eral_exchange_rate;

ALTER TABLE finance.tax_rates
    DROP CONSTRAINT IF EXISTS fk_tax_rate_tax_type;

ALTER TABLE finance.tax_rate_audit_logs
    DROP CONSTRAINT IF EXISTS fk_tral_tax_rate;

DROP INDEX IF EXISTS finance.uq_tax_rate_open_active;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'finance' AND table_name = 'tax_calculation_log'
    ) THEN
        ALTER TABLE finance.tax_calculation_log
            DROP CONSTRAINT IF EXISTS fk_tax_log_tax_type;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. Migrate PKs — parent first
-- ---------------------------------------------------------------------------

-- finance.currencies
ALTER TABLE finance.currencies ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY code, id) AS rn
    FROM finance.currencies
)
UPDATE finance.currencies c
SET id_bigint = r.rn
FROM ranked r
WHERE c.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'currency', id, id_bigint
FROM finance.currencies
ON CONFLICT DO NOTHING;

-- finance.customers
ALTER TABLE finance.customers ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY name, id) AS rn
    FROM finance.customers
)
UPDATE finance.customers c
SET id_bigint = r.rn
FROM ranked r
WHERE c.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'customer', id, id_bigint
FROM finance.customers
ON CONFLICT DO NOTHING;

-- finance.tax_types
ALTER TABLE finance.tax_types ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY code, id) AS rn
    FROM finance.tax_types
)
UPDATE finance.tax_types t
SET id_bigint = r.rn
FROM ranked r
WHERE t.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'tax_types', id, id_bigint
FROM finance.tax_types
ON CONFLICT DO NOTHING;

-- finance.tax_type (V9 audit table — if present)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'finance' AND table_name = 'tax_type'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_type ADD COLUMN id_bigint BIGINT;

            WITH ranked AS (
                SELECT id, ROW_NUMBER() OVER (ORDER BY code, id) AS rn
                FROM finance.tax_type
            )
            UPDATE finance.tax_type t
            SET id_bigint = r.rn
            FROM ranked r
            WHERE t.id = r.id;

            INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
            SELECT 'tax_type', id, id_bigint
            FROM finance.tax_type
            ON CONFLICT DO NOTHING;
        $sql$;
    END IF;
END $$;

-- finance.system_config
ALTER TABLE finance.system_config ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM finance.system_config
)
UPDATE finance.system_config s
SET id_bigint = r.rn
FROM ranked r
WHERE s.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'system_config', id, id_bigint
FROM finance.system_config
ON CONFLICT DO NOTHING;

-- finance.contracts
ALTER TABLE finance.contracts ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY contract_code, id) AS rn
    FROM finance.contracts
)
UPDATE finance.contracts c
SET id_bigint = r.rn
FROM ranked r
WHERE c.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'contract', id, id_bigint
FROM finance.contracts
ON CONFLICT DO NOTHING;

-- finance.exchange_rates
ALTER TABLE finance.exchange_rates ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY rate_date, source_currency_id, target_currency_id, id) AS rn
    FROM finance.exchange_rates
)
UPDATE finance.exchange_rates e
SET id_bigint = r.rn
FROM ranked r
WHERE e.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'exchange_rate', id, id_bigint
FROM finance.exchange_rates
ON CONFLICT DO NOTHING;

-- finance.financial_transactions
ALTER TABLE finance.financial_transactions ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY transaction_date, id) AS rn
    FROM finance.financial_transactions
)
UPDATE finance.financial_transactions f
SET id_bigint = r.rn
FROM ranked r
WHERE f.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'financial_transaction', id, id_bigint
FROM finance.financial_transactions
ON CONFLICT DO NOTHING;

-- finance.customer_permitted_currencies
ALTER TABLE finance.customer_permitted_currencies ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY customer_id, currency_id, id) AS rn
    FROM finance.customer_permitted_currencies
)
UPDATE finance.customer_permitted_currencies c
SET id_bigint = r.rn
FROM ranked r
WHERE c.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'customer_permitted_currency', id, id_bigint
FROM finance.customer_permitted_currencies
ON CONFLICT DO NOTHING;

-- finance.exchange_rate_audit_logs
ALTER TABLE finance.exchange_rate_audit_logs ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY changed_at, id) AS rn
    FROM finance.exchange_rate_audit_logs
)
UPDATE finance.exchange_rate_audit_logs e
SET id_bigint = r.rn
FROM ranked r
WHERE e.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'exchange_rate_audit_log', id, id_bigint
FROM finance.exchange_rate_audit_logs
ON CONFLICT DO NOTHING;

-- finance.tax_rates
ALTER TABLE finance.tax_rates ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY start_date, tax_type_id, id) AS rn
    FROM finance.tax_rates
)
UPDATE finance.tax_rates t
SET id_bigint = r.rn
FROM ranked r
WHERE t.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'tax_rate', id, id_bigint
FROM finance.tax_rates
ON CONFLICT DO NOTHING;

-- finance.tax_rate_audit_logs
ALTER TABLE finance.tax_rate_audit_logs ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY changed_at, id) AS rn
    FROM finance.tax_rate_audit_logs
)
UPDATE finance.tax_rate_audit_logs t
SET id_bigint = r.rn
FROM ranked r
WHERE t.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'tax_rate_audit_log', id, id_bigint
FROM finance.tax_rate_audit_logs
ON CONFLICT DO NOTHING;

-- finance.tax_calculation_log (if present)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'finance' AND table_name = 'tax_calculation_log'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_calculation_log ADD COLUMN id_bigint BIGINT;

            WITH ranked AS (
                SELECT id, ROW_NUMBER() OVER (ORDER BY calculation_date, id) AS rn
                FROM finance.tax_calculation_log
            )
            UPDATE finance.tax_calculation_log t
            SET id_bigint = r.rn
            FROM ranked r
            WHERE t.id = r.id;

            INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
            SELECT 'tax_calculation_log', id, id_bigint
            FROM finance.tax_calculation_log
            ON CONFLICT DO NOTHING;
        $sql$;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 4. Migrate FK / cross-service columns
-- ---------------------------------------------------------------------------

-- system_config.default_currency_id
ALTER TABLE finance.system_config ADD COLUMN default_currency_id_bigint BIGINT;
UPDATE finance.system_config s
SET default_currency_id_bigint = map_uuid('currency', s.default_currency_id);

-- company_currency_settings (PK = company_id from core)
ALTER TABLE finance.company_currency_settings
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN base_currency_id_bigint BIGINT;

UPDATE finance.company_currency_settings s
SET company_id_bigint = map_uuid('company', s.company_id),
    base_currency_id_bigint = map_uuid('currency', s.base_currency_id);

-- location_currency_settings (PK = location_id from core)
ALTER TABLE finance.location_currency_settings
    ADD COLUMN location_id_bigint BIGINT,
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN local_currency_id_bigint BIGINT;

UPDATE finance.location_currency_settings s
SET location_id_bigint = map_uuid('location', s.location_id),
    company_id_bigint = map_uuid('company', s.company_id),
    local_currency_id_bigint = map_uuid('currency', s.local_currency_id);

-- customers currency FKs
ALTER TABLE finance.customers
    ADD COLUMN default_currency_id_bigint BIGINT,
    ADD COLUMN invoicing_currency_id_bigint BIGINT;

UPDATE finance.customers c
SET default_currency_id_bigint = map_uuid('currency', c.default_currency_id),
    invoicing_currency_id_bigint = map_uuid('currency', c.invoicing_currency_id);

-- contracts
ALTER TABLE finance.contracts
    ADD COLUMN customer_id_bigint BIGINT,
    ADD COLUMN currency_id_bigint BIGINT;

UPDATE finance.contracts c
SET customer_id_bigint = map_uuid('customer', c.customer_id),
    currency_id_bigint = map_uuid('currency', c.currency_id);

-- exchange_rates
ALTER TABLE finance.exchange_rates
    ADD COLUMN source_currency_id_bigint BIGINT,
    ADD COLUMN target_currency_id_bigint BIGINT;

UPDATE finance.exchange_rates e
SET source_currency_id_bigint = map_uuid('currency', e.source_currency_id),
    target_currency_id_bigint = map_uuid('currency', e.target_currency_id);

-- financial_transactions
ALTER TABLE finance.financial_transactions
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN location_id_bigint BIGINT,
    ADD COLUMN contract_id_bigint BIGINT,
    ADD COLUMN original_currency_id_bigint BIGINT,
    ADD COLUMN base_currency_id_bigint BIGINT;

UPDATE finance.financial_transactions f
SET company_id_bigint = map_uuid('company', f.company_id),
    location_id_bigint = map_uuid('location', f.location_id),
    contract_id_bigint = map_uuid('contract', f.contract_id),
    original_currency_id_bigint = map_uuid('currency', f.original_currency_id),
    base_currency_id_bigint = map_uuid('currency', f.base_currency_id);

-- customer_permitted_currencies
ALTER TABLE finance.customer_permitted_currencies
    ADD COLUMN customer_id_bigint BIGINT,
    ADD COLUMN currency_id_bigint BIGINT;

UPDATE finance.customer_permitted_currencies c
SET customer_id_bigint = map_uuid('customer', c.customer_id),
    currency_id_bigint = map_uuid('currency', c.currency_id);

-- exchange_rate_audit_logs
ALTER TABLE finance.exchange_rate_audit_logs
    ADD COLUMN exchange_rate_id_bigint BIGINT,
    ADD COLUMN user_id_bigint BIGINT;

UPDATE finance.exchange_rate_audit_logs e
SET exchange_rate_id_bigint = map_uuid('exchange_rate', e.exchange_rate_id),
    user_id_bigint = map_uuid('user', e.user_id);

-- tax_rates
ALTER TABLE finance.tax_rates
    ADD COLUMN tax_type_id_bigint BIGINT,
    ADD COLUMN country_id_bigint BIGINT,
    ADD COLUMN location_id_bigint BIGINT,
    ADD COLUMN customer_id_bigint BIGINT;

UPDATE finance.tax_rates t
SET tax_type_id_bigint = map_uuid('tax_types', t.tax_type_id),
    country_id_bigint = map_uuid('country', t.country_id),
    location_id_bigint = map_uuid('location', t.location_id),
    customer_id_bigint = map_uuid('customer', t.customer_id);

-- tax_rate_audit_logs
ALTER TABLE finance.tax_rate_audit_logs
    ADD COLUMN tax_rate_id_bigint BIGINT,
    ADD COLUMN user_id_bigint BIGINT;

UPDATE finance.tax_rate_audit_logs t
SET tax_rate_id_bigint = map_uuid('tax_rate', t.tax_rate_id),
    user_id_bigint = map_uuid('user', t.user_id);

-- tax_calculation_log FKs
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'finance' AND table_name = 'tax_calculation_log'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_calculation_log
                ADD COLUMN tax_type_id_bigint BIGINT,
                ADD COLUMN transaction_reference_id_bigint BIGINT;

            UPDATE finance.tax_calculation_log t
            SET tax_type_id_bigint = map_uuid('tax_type', t.tax_type_id);

            -- Cross-module reference: opaque per-type mapping for external PKs
            INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
            SELECT entity_type, old_uuid, new_id
            FROM (
                SELECT
                    'tx_ref_' || lower(transaction_type) AS entity_type,
                    transaction_reference_id AS old_uuid,
                    ROW_NUMBER() OVER (
                        PARTITION BY lower(transaction_type)
                        ORDER BY transaction_reference_id
                    ) AS new_id
                FROM finance.tax_calculation_log
                WHERE transaction_reference_id IS NOT NULL
            ) mapped
            ON CONFLICT DO NOTHING;

            UPDATE finance.tax_calculation_log t
            SET transaction_reference_id_bigint = m.new_id
            FROM id_legacy_map m
            WHERE m.entity_type = 'tx_ref_' || lower(t.transaction_type)
              AND m.old_uuid = t.transaction_reference_id;
        $sql$;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- ---------------------------------------------------------------------------
-- 4b. Legacy demo satırları (ör. V2 cccccccc şirket UUID'si) core map'te yoksa sil
-- ---------------------------------------------------------------------------
DELETE FROM finance.company_currency_settings
WHERE company_id_bigint IS NULL OR base_currency_id_bigint IS NULL;

DELETE FROM finance.location_currency_settings
WHERE location_id_bigint IS NULL
   OR company_id_bigint IS NULL
   OR local_currency_id_bigint IS NULL;

-- ---------------------------------------------------------------------------
-- 5. Swap UUID columns → BIGINT (drop old, rename new)
-- ---------------------------------------------------------------------------
-- ---------------------------------------------------------------------------

-- currencies
ALTER TABLE finance.currencies DROP CONSTRAINT pk_currencies;
ALTER TABLE finance.currencies DROP COLUMN id;
ALTER TABLE finance.currencies RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.currencies ADD CONSTRAINT pk_currencies PRIMARY KEY (id);

-- customers
ALTER TABLE finance.customers DROP CONSTRAINT pk_finance_customers;
ALTER TABLE finance.customers DROP COLUMN id;
ALTER TABLE finance.customers RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.customers
    DROP COLUMN default_currency_id,
    DROP COLUMN invoicing_currency_id;
ALTER TABLE finance.customers
    RENAME COLUMN default_currency_id_bigint TO default_currency_id;
ALTER TABLE finance.customers
    RENAME COLUMN invoicing_currency_id_bigint TO invoicing_currency_id;
ALTER TABLE finance.customers ADD CONSTRAINT pk_finance_customers PRIMARY KEY (id);

-- tax_types
ALTER TABLE finance.tax_types DROP CONSTRAINT pk_tax_types;
ALTER TABLE finance.tax_types DROP COLUMN id;
ALTER TABLE finance.tax_types RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.tax_types ADD CONSTRAINT pk_tax_types PRIMARY KEY (id);

-- tax_type (V9)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'finance' AND table_name = 'tax_type' AND column_name = 'id_bigint'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_type DROP CONSTRAINT pk_tax_type_audit;
            ALTER TABLE finance.tax_type DROP COLUMN id;
            ALTER TABLE finance.tax_type RENAME COLUMN id_bigint TO id;
            ALTER TABLE finance.tax_type ADD CONSTRAINT pk_tax_type_audit PRIMARY KEY (id);
        $sql$;
    END IF;
END $$;

-- system_config
ALTER TABLE finance.system_config DROP CONSTRAINT pk_system_config;
ALTER TABLE finance.system_config DROP COLUMN id;
ALTER TABLE finance.system_config RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.system_config DROP COLUMN default_currency_id;
ALTER TABLE finance.system_config RENAME COLUMN default_currency_id_bigint TO default_currency_id;
ALTER TABLE finance.system_config ADD CONSTRAINT pk_system_config PRIMARY KEY (id);

-- company_currency_settings
ALTER TABLE finance.company_currency_settings DROP CONSTRAINT pk_company_currency_settings;
ALTER TABLE finance.company_currency_settings DROP COLUMN company_id;
ALTER TABLE finance.company_currency_settings DROP COLUMN base_currency_id;
ALTER TABLE finance.company_currency_settings RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE finance.company_currency_settings RENAME COLUMN base_currency_id_bigint TO base_currency_id;
ALTER TABLE finance.company_currency_settings ADD CONSTRAINT pk_company_currency_settings PRIMARY KEY (company_id);

-- location_currency_settings
ALTER TABLE finance.location_currency_settings DROP CONSTRAINT pk_location_currency_settings;
ALTER TABLE finance.location_currency_settings DROP COLUMN location_id;
ALTER TABLE finance.location_currency_settings DROP COLUMN company_id;
ALTER TABLE finance.location_currency_settings DROP COLUMN local_currency_id;
ALTER TABLE finance.location_currency_settings RENAME COLUMN location_id_bigint TO location_id;
ALTER TABLE finance.location_currency_settings RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE finance.location_currency_settings RENAME COLUMN local_currency_id_bigint TO local_currency_id;
ALTER TABLE finance.location_currency_settings ADD CONSTRAINT pk_location_currency_settings PRIMARY KEY (location_id);

-- contracts
ALTER TABLE finance.contracts DROP CONSTRAINT pk_contracts;
ALTER TABLE finance.contracts DROP COLUMN id;
ALTER TABLE finance.contracts RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.contracts DROP COLUMN customer_id;
ALTER TABLE finance.contracts DROP COLUMN currency_id;
ALTER TABLE finance.contracts RENAME COLUMN customer_id_bigint TO customer_id;
ALTER TABLE finance.contracts RENAME COLUMN currency_id_bigint TO currency_id;
ALTER TABLE finance.contracts ADD CONSTRAINT pk_contracts PRIMARY KEY (id);

-- exchange_rates
ALTER TABLE finance.exchange_rates DROP CONSTRAINT pk_exchange_rates;
ALTER TABLE finance.exchange_rates DROP COLUMN id;
ALTER TABLE finance.exchange_rates RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.exchange_rates DROP COLUMN source_currency_id;
ALTER TABLE finance.exchange_rates DROP COLUMN target_currency_id;
ALTER TABLE finance.exchange_rates RENAME COLUMN source_currency_id_bigint TO source_currency_id;
ALTER TABLE finance.exchange_rates RENAME COLUMN target_currency_id_bigint TO target_currency_id;
ALTER TABLE finance.exchange_rates ADD CONSTRAINT pk_exchange_rates PRIMARY KEY (id);

-- financial_transactions
ALTER TABLE finance.financial_transactions DROP CONSTRAINT pk_financial_transactions;
ALTER TABLE finance.financial_transactions DROP COLUMN id;
ALTER TABLE finance.financial_transactions RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.financial_transactions
    DROP COLUMN company_id,
    DROP COLUMN location_id,
    DROP COLUMN contract_id,
    DROP COLUMN original_currency_id,
    DROP COLUMN base_currency_id;
ALTER TABLE finance.financial_transactions RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE finance.financial_transactions RENAME COLUMN location_id_bigint TO location_id;
ALTER TABLE finance.financial_transactions RENAME COLUMN contract_id_bigint TO contract_id;
ALTER TABLE finance.financial_transactions RENAME COLUMN original_currency_id_bigint TO original_currency_id;
ALTER TABLE finance.financial_transactions RENAME COLUMN base_currency_id_bigint TO base_currency_id;
ALTER TABLE finance.financial_transactions ADD CONSTRAINT pk_financial_transactions PRIMARY KEY (id);

-- customer_permitted_currencies
ALTER TABLE finance.customer_permitted_currencies DROP CONSTRAINT pk_customer_permitted_currencies;
ALTER TABLE finance.customer_permitted_currencies DROP COLUMN id;
ALTER TABLE finance.customer_permitted_currencies RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.customer_permitted_currencies DROP COLUMN customer_id;
ALTER TABLE finance.customer_permitted_currencies DROP COLUMN currency_id;
ALTER TABLE finance.customer_permitted_currencies RENAME COLUMN customer_id_bigint TO customer_id;
ALTER TABLE finance.customer_permitted_currencies RENAME COLUMN currency_id_bigint TO currency_id;
ALTER TABLE finance.customer_permitted_currencies ADD CONSTRAINT pk_customer_permitted_currencies PRIMARY KEY (id);

-- exchange_rate_audit_logs
ALTER TABLE finance.exchange_rate_audit_logs DROP CONSTRAINT pk_exchange_rate_audit_logs;
ALTER TABLE finance.exchange_rate_audit_logs DROP COLUMN id;
ALTER TABLE finance.exchange_rate_audit_logs RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.exchange_rate_audit_logs DROP COLUMN exchange_rate_id;
ALTER TABLE finance.exchange_rate_audit_logs DROP COLUMN user_id;
ALTER TABLE finance.exchange_rate_audit_logs RENAME COLUMN exchange_rate_id_bigint TO exchange_rate_id;
ALTER TABLE finance.exchange_rate_audit_logs RENAME COLUMN user_id_bigint TO user_id;
ALTER TABLE finance.exchange_rate_audit_logs ADD CONSTRAINT pk_exchange_rate_audit_logs PRIMARY KEY (id);

-- tax_rates
ALTER TABLE finance.tax_rates DROP CONSTRAINT pk_tax_rates;
ALTER TABLE finance.tax_rates DROP COLUMN id;
ALTER TABLE finance.tax_rates RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.tax_rates
    DROP COLUMN tax_type_id,
    DROP COLUMN country_id,
    DROP COLUMN location_id,
    DROP COLUMN customer_id;
ALTER TABLE finance.tax_rates RENAME COLUMN tax_type_id_bigint TO tax_type_id;
ALTER TABLE finance.tax_rates RENAME COLUMN country_id_bigint TO country_id;
ALTER TABLE finance.tax_rates RENAME COLUMN location_id_bigint TO location_id;
ALTER TABLE finance.tax_rates RENAME COLUMN customer_id_bigint TO customer_id;
ALTER TABLE finance.tax_rates ADD CONSTRAINT pk_tax_rates PRIMARY KEY (id);

-- tax_rate_audit_logs
ALTER TABLE finance.tax_rate_audit_logs DROP CONSTRAINT pk_tax_rate_audit_logs;
ALTER TABLE finance.tax_rate_audit_logs DROP COLUMN id;
ALTER TABLE finance.tax_rate_audit_logs RENAME COLUMN id_bigint TO id;
ALTER TABLE finance.tax_rate_audit_logs DROP COLUMN tax_rate_id;
ALTER TABLE finance.tax_rate_audit_logs DROP COLUMN user_id;
ALTER TABLE finance.tax_rate_audit_logs RENAME COLUMN tax_rate_id_bigint TO tax_rate_id;
ALTER TABLE finance.tax_rate_audit_logs RENAME COLUMN user_id_bigint TO user_id;
ALTER TABLE finance.tax_rate_audit_logs ADD CONSTRAINT pk_tax_rate_audit_logs PRIMARY KEY (id);

-- tax_calculation_log
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'finance' AND table_name = 'tax_calculation_log' AND column_name = 'id_bigint'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_calculation_log DROP CONSTRAINT pk_tax_calculation_log;
            ALTER TABLE finance.tax_calculation_log DROP COLUMN id;
            ALTER TABLE finance.tax_calculation_log RENAME COLUMN id_bigint TO id;
            ALTER TABLE finance.tax_calculation_log DROP COLUMN tax_type_id;
            ALTER TABLE finance.tax_calculation_log DROP COLUMN transaction_reference_id;
            ALTER TABLE finance.tax_calculation_log RENAME COLUMN tax_type_id_bigint TO tax_type_id;
            ALTER TABLE finance.tax_calculation_log RENAME COLUMN transaction_reference_id_bigint TO transaction_reference_id;
            ALTER TABLE finance.tax_calculation_log ADD CONSTRAINT pk_tax_calculation_log PRIMARY KEY (id);
        $sql$;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 6. Recreate FK constraints and indexes
-- ---------------------------------------------------------------------------
ALTER TABLE finance.system_config
    ADD CONSTRAINT fk_system_config_currency
        FOREIGN KEY (default_currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.company_currency_settings
    ADD CONSTRAINT fk_ccs_base_currency
        FOREIGN KEY (base_currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.location_currency_settings
    ADD CONSTRAINT fk_lcs_local_currency
        FOREIGN KEY (local_currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.customers
    ADD CONSTRAINT fk_customers_default_currency
        FOREIGN KEY (default_currency_id) REFERENCES finance.currencies (id),
    ADD CONSTRAINT fk_customers_invoicing_currency
        FOREIGN KEY (invoicing_currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.contracts
    ADD CONSTRAINT fk_contracts_customer
        FOREIGN KEY (customer_id) REFERENCES finance.customers (id),
    ADD CONSTRAINT fk_contracts_currency
        FOREIGN KEY (currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.exchange_rates
    ADD CONSTRAINT fk_er_source_currency
        FOREIGN KEY (source_currency_id) REFERENCES finance.currencies (id),
    ADD CONSTRAINT fk_er_target_currency
        FOREIGN KEY (target_currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.financial_transactions
    ADD CONSTRAINT fk_ft_contract
        FOREIGN KEY (contract_id) REFERENCES finance.contracts (id),
    ADD CONSTRAINT fk_ft_original_currency
        FOREIGN KEY (original_currency_id) REFERENCES finance.currencies (id),
    ADD CONSTRAINT fk_ft_base_currency
        FOREIGN KEY (base_currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.customer_permitted_currencies
    ADD CONSTRAINT fk_cpc_customer
        FOREIGN KEY (customer_id) REFERENCES finance.customers (id),
    ADD CONSTRAINT fk_cpc_currency
        FOREIGN KEY (currency_id) REFERENCES finance.currencies (id);

ALTER TABLE finance.exchange_rate_audit_logs
    ADD CONSTRAINT fk_eral_exchange_rate
        FOREIGN KEY (exchange_rate_id) REFERENCES finance.exchange_rates (id);

ALTER TABLE finance.tax_rates
    ADD CONSTRAINT fk_tax_rate_tax_type
        FOREIGN KEY (tax_type_id) REFERENCES finance.tax_types (id);

ALTER TABLE finance.tax_rate_audit_logs
    ADD CONSTRAINT fk_tral_tax_rate
        FOREIGN KEY (tax_rate_id) REFERENCES finance.tax_rates (id);

CREATE UNIQUE INDEX uq_tax_rate_open_active
    ON finance.tax_rates (tax_type_id, country_id,
        COALESCE(location_id, 0),
        COALESCE(customer_id, 0),
        COALESCE(product_type, ''),
        COALESCE(operation_type, ''))
    WHERE end_date IS NULL AND is_active = TRUE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'finance' AND table_name = 'tax_calculation_log'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_calculation_log
                ADD CONSTRAINT fk_tax_log_tax_type
                    FOREIGN KEY (tax_type_id) REFERENCES finance.tax_type (id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT;
        $sql$;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 7. BIGINT IDENTITY on PK columns
-- ---------------------------------------------------------------------------
ALTER TABLE finance.currencies ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.currencies', 'id'), COALESCE((SELECT MAX(id) FROM finance.currencies), 1));

ALTER TABLE finance.customers ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.customers', 'id'), COALESCE((SELECT MAX(id) FROM finance.customers), 1));

ALTER TABLE finance.tax_types ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.tax_types', 'id'), COALESCE((SELECT MAX(id) FROM finance.tax_types), 1));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'finance' AND table_name = 'tax_type'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_type ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
            SELECT setval(pg_get_serial_sequence('finance.tax_type', 'id'), COALESCE((SELECT MAX(id) FROM finance.tax_type), 1));
        $sql$;
    END IF;
END $$;

ALTER TABLE finance.system_config ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.system_config', 'id'), COALESCE((SELECT MAX(id) FROM finance.system_config), 1));

ALTER TABLE finance.contracts ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.contracts', 'id'), COALESCE((SELECT MAX(id) FROM finance.contracts), 1));

ALTER TABLE finance.exchange_rates ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.exchange_rates', 'id'), COALESCE((SELECT MAX(id) FROM finance.exchange_rates), 1));

ALTER TABLE finance.financial_transactions ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.financial_transactions', 'id'), COALESCE((SELECT MAX(id) FROM finance.financial_transactions), 1));

ALTER TABLE finance.customer_permitted_currencies ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.customer_permitted_currencies', 'id'), COALESCE((SELECT MAX(id) FROM finance.customer_permitted_currencies), 1));

ALTER TABLE finance.exchange_rate_audit_logs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.exchange_rate_audit_logs', 'id'), COALESCE((SELECT MAX(id) FROM finance.exchange_rate_audit_logs), 1));

ALTER TABLE finance.tax_rates ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.tax_rates', 'id'), COALESCE((SELECT MAX(id) FROM finance.tax_rates), 1));

ALTER TABLE finance.tax_rate_audit_logs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('finance.tax_rate_audit_logs', 'id'), COALESCE((SELECT MAX(id) FROM finance.tax_rate_audit_logs), 1));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'finance' AND table_name = 'tax_calculation_log'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE finance.tax_calculation_log ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
            SELECT setval(pg_get_serial_sequence('finance.tax_calculation_log', 'id'), COALESCE((SELECT MAX(id) FROM finance.tax_calculation_log), 1));
        $sql$;
    END IF;
END $$;

DROP FUNCTION map_uuid(VARCHAR, UUID);

COMMIT;
