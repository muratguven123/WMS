-- =============================================================================
-- V3 — UUID → BIGINT migration (wms_billing_db)
-- Requires wms_core_db V16 and wms_finance_db V11 to have run first.
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

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
    'SELECT entity_type, old_uuid, new_id FROM public.id_legacy_map'
) AS t(entity_type VARCHAR(50), old_uuid UUID, new_id BIGINT);

-- Finance customer mappings (cross-service)
INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT entity_type, old_uuid, new_id
FROM dblink(
    'host=localhost dbname=wms_finance_db user=postgres password=postgres',
    'SELECT entity_type, old_uuid, new_id FROM public.id_legacy_map WHERE entity_type = ''customer'''
) AS t(entity_type VARCHAR(50), old_uuid UUID, new_id BIGINT)
ON CONFLICT DO NOTHING;

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
-- Drop FK constraints
-- ---------------------------------------------------------------------------
ALTER TABLE invoice_items DROP CONSTRAINT IF EXISTS fk_invoice_items_invoice;
ALTER TABLE exchange_difference_logs DROP CONSTRAINT IF EXISTS fk_exch_diff_logs_invoice;

-- ---------------------------------------------------------------------------
-- Migrate PKs (parent first)
-- ---------------------------------------------------------------------------
ALTER TABLE invoices ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM invoices
)
UPDATE invoices i
SET id_bigint = r.rn
FROM ranked r
WHERE i.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'invoice', id, id_bigint
FROM invoices
ON CONFLICT DO NOTHING;

ALTER TABLE invoice_items ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY invoice_id, id) AS rn
    FROM invoice_items
)
UPDATE invoice_items i
SET id_bigint = r.rn
FROM ranked r
WHERE i.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'invoice_item', id, id_bigint
FROM invoice_items
ON CONFLICT DO NOTHING;

ALTER TABLE exchange_difference_logs ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY calculation_date, id) AS rn
    FROM exchange_difference_logs
)
UPDATE exchange_difference_logs e
SET id_bigint = r.rn
FROM ranked r
WHERE e.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'exchange_difference_log', id, id_bigint
FROM exchange_difference_logs
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Migrate FK / cross-service columns
-- ---------------------------------------------------------------------------
ALTER TABLE invoices
    ADD COLUMN customer_id_bigint BIGINT,
    ADD COLUMN location_id_bigint BIGINT;

UPDATE invoices i
SET customer_id_bigint = map_uuid('customer', i.customer_id),
    location_id_bigint = map_uuid('location', i.location_id);

ALTER TABLE invoice_items ADD COLUMN invoice_id_bigint BIGINT;

UPDATE invoice_items ii
SET invoice_id_bigint = map_uuid('invoice', ii.invoice_id);

ALTER TABLE exchange_difference_logs ADD COLUMN invoice_id_bigint BIGINT;

UPDATE exchange_difference_logs e
SET invoice_id_bigint = map_uuid('invoice', e.invoice_id);

-- ---------------------------------------------------------------------------
-- Swap columns
-- ---------------------------------------------------------------------------
ALTER TABLE invoices DROP CONSTRAINT pk_invoices;
ALTER TABLE invoices DROP COLUMN id;
ALTER TABLE invoices RENAME COLUMN id_bigint TO id;
ALTER TABLE invoices DROP COLUMN customer_id;
ALTER TABLE invoices DROP COLUMN location_id;
ALTER TABLE invoices RENAME COLUMN customer_id_bigint TO customer_id;
ALTER TABLE invoices RENAME COLUMN location_id_bigint TO location_id;
ALTER TABLE invoices ADD CONSTRAINT pk_invoices PRIMARY KEY (id);

ALTER TABLE invoice_items DROP CONSTRAINT pk_invoice_items;
ALTER TABLE invoice_items DROP COLUMN id;
ALTER TABLE invoice_items RENAME COLUMN id_bigint TO id;
ALTER TABLE invoice_items DROP COLUMN invoice_id;
ALTER TABLE invoice_items RENAME COLUMN invoice_id_bigint TO invoice_id;
ALTER TABLE invoice_items ADD CONSTRAINT pk_invoice_items PRIMARY KEY (id);

ALTER TABLE exchange_difference_logs DROP CONSTRAINT pk_exchange_difference_logs;
ALTER TABLE exchange_difference_logs DROP COLUMN id;
ALTER TABLE exchange_difference_logs RENAME COLUMN id_bigint TO id;
ALTER TABLE exchange_difference_logs DROP COLUMN invoice_id;
ALTER TABLE exchange_difference_logs RENAME COLUMN invoice_id_bigint TO invoice_id;
ALTER TABLE exchange_difference_logs ADD CONSTRAINT pk_exchange_difference_logs PRIMARY KEY (id);

-- ---------------------------------------------------------------------------
-- Recreate FK constraints
-- ---------------------------------------------------------------------------
ALTER TABLE invoice_items
    ADD CONSTRAINT fk_invoice_items_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE;

ALTER TABLE exchange_difference_logs
    ADD CONSTRAINT fk_exch_diff_logs_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- BIGINT IDENTITY
-- ---------------------------------------------------------------------------
ALTER TABLE invoices ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('invoices', 'id'), COALESCE((SELECT MAX(id) FROM invoices), 1));

ALTER TABLE invoice_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('invoice_items', 'id'), COALESCE((SELECT MAX(id) FROM invoice_items), 1));

ALTER TABLE exchange_difference_logs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('exchange_difference_logs', 'id'), COALESCE((SELECT MAX(id) FROM exchange_difference_logs), 1));

DROP FUNCTION map_uuid(VARCHAR, UUID);

COMMIT;
