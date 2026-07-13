-- =============================================================================
-- V2 — UUID → BIGINT migration (wms_inventory_db)
-- Requires wms_core_db V16__uuid_to_bigint.sql to have run first.
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
-- Migrate PKs
-- ---------------------------------------------------------------------------
ALTER TABLE inventories ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY updated_at, id) AS rn
    FROM inventories
)
UPDATE inventories i
SET id_bigint = r.rn
FROM ranked r
WHERE i.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'inventory', id, id_bigint
FROM inventories
ON CONFLICT DO NOTHING;

ALTER TABLE inventory_transactions ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY transaction_date, id) AS rn
    FROM inventory_transactions
)
UPDATE inventory_transactions t
SET id_bigint = r.rn
FROM ranked r
WHERE t.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'inventory_transaction', id, id_bigint
FROM inventory_transactions
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Migrate cross-service columns (core id_legacy_map)
-- ---------------------------------------------------------------------------
ALTER TABLE inventories
    ADD COLUMN storage_location_id_bigint BIGINT,
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN warehouse_location_id_bigint BIGINT;

UPDATE inventories i
SET storage_location_id_bigint = map_uuid('storage_location', i.storage_location_id),
    company_id_bigint = map_uuid('company', i.company_id),
    warehouse_location_id_bigint = map_uuid('location', i.warehouse_location_id);

ALTER TABLE inventory_transactions
    ADD COLUMN source_location_id_bigint BIGINT,
    ADD COLUMN target_location_id_bigint BIGINT,
    ADD COLUMN performed_by_user_id_bigint BIGINT;

UPDATE inventory_transactions t
SET source_location_id_bigint = map_uuid('storage_location', t.source_location_id),
    target_location_id_bigint = map_uuid('storage_location', t.target_location_id),
    performed_by_user_id_bigint = map_uuid('user', t.performed_by_user_id);

-- ---------------------------------------------------------------------------
-- Swap columns
-- ---------------------------------------------------------------------------
ALTER TABLE inventories DROP CONSTRAINT pk_inventories;
ALTER TABLE inventories DROP CONSTRAINT uq_inventory_location_product_lot_serial;
ALTER TABLE inventories DROP COLUMN id;
ALTER TABLE inventories RENAME COLUMN id_bigint TO id;
ALTER TABLE inventories
    DROP COLUMN storage_location_id,
    DROP COLUMN company_id,
    DROP COLUMN warehouse_location_id;
ALTER TABLE inventories RENAME COLUMN storage_location_id_bigint TO storage_location_id;
ALTER TABLE inventories RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE inventories RENAME COLUMN warehouse_location_id_bigint TO warehouse_location_id;
ALTER TABLE inventories ADD CONSTRAINT pk_inventories PRIMARY KEY (id);
ALTER TABLE inventories
    ADD CONSTRAINT uq_inventory_location_product_lot_serial
        UNIQUE (storage_location_id, product_code, lot_number, serial_number);

ALTER TABLE inventory_transactions DROP CONSTRAINT pk_inventory_transactions;
ALTER TABLE inventory_transactions DROP COLUMN id;
ALTER TABLE inventory_transactions RENAME COLUMN id_bigint TO id;
ALTER TABLE inventory_transactions
    DROP COLUMN source_location_id,
    DROP COLUMN target_location_id,
    DROP COLUMN performed_by_user_id;
ALTER TABLE inventory_transactions RENAME COLUMN source_location_id_bigint TO source_location_id;
ALTER TABLE inventory_transactions RENAME COLUMN target_location_id_bigint TO target_location_id;
ALTER TABLE inventory_transactions RENAME COLUMN performed_by_user_id_bigint TO performed_by_user_id;
ALTER TABLE inventory_transactions ADD CONSTRAINT pk_inventory_transactions PRIMARY KEY (id);

-- ---------------------------------------------------------------------------
-- BIGINT IDENTITY
-- ---------------------------------------------------------------------------
ALTER TABLE inventories ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('inventories', 'id'), COALESCE((SELECT MAX(id) FROM inventories), 1));

ALTER TABLE inventory_transactions ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('inventory_transactions', 'id'), COALESCE((SELECT MAX(id) FROM inventory_transactions), 1));

DROP FUNCTION map_uuid(VARCHAR, UUID);

COMMIT;
