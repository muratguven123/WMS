-- =============================================================================
-- V4 — UUID → BIGINT migration (wms_inbound_db)
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
-- Drop FK constraints
-- ---------------------------------------------------------------------------
ALTER TABLE inbound_order_items DROP CONSTRAINT IF EXISTS fk_item_inbound_order;
ALTER TABLE receipts DROP CONSTRAINT IF EXISTS fk_receipt_inbound_order;
ALTER TABLE receipt_items DROP CONSTRAINT IF EXISTS fk_item_receipt;

-- ---------------------------------------------------------------------------
-- Migrate PKs (parent first)
-- ---------------------------------------------------------------------------
ALTER TABLE inbound_orders ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM inbound_orders
)
UPDATE inbound_orders o
SET id_bigint = r.rn
FROM ranked r
WHERE o.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'inbound_order', id, id_bigint
FROM inbound_orders
ON CONFLICT DO NOTHING;

ALTER TABLE inbound_order_items ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY inbound_order_id, id) AS rn
    FROM inbound_order_items
)
UPDATE inbound_order_items o
SET id_bigint = r.rn
FROM ranked r
WHERE o.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'inbound_order_item', id, id_bigint
FROM inbound_order_items
ON CONFLICT DO NOTHING;

ALTER TABLE receipts ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY received_at, id) AS rn
    FROM receipts
)
UPDATE receipts r
SET id_bigint = rk.rn
FROM ranked rk
WHERE r.id = rk.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'receipt', id, id_bigint
FROM receipts
ON CONFLICT DO NOTHING;

ALTER TABLE receipt_items ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY receipt_id, id) AS rn
    FROM receipt_items
)
UPDATE receipt_items ri
SET id_bigint = r.rn
FROM ranked r
WHERE ri.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'receipt_item', id, id_bigint
FROM receipt_items
ON CONFLICT DO NOTHING;

ALTER TABLE outbox_messages ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM outbox_messages
)
UPDATE outbox_messages o
SET id_bigint = r.rn
FROM ranked r
WHERE o.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'outbox_message', id, id_bigint
FROM outbox_messages
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Migrate FK / cross-service columns
-- ---------------------------------------------------------------------------
ALTER TABLE inbound_orders
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN warehouse_location_id_bigint BIGINT;

UPDATE inbound_orders o
SET company_id_bigint = map_uuid('company', o.company_id),
    warehouse_location_id_bigint = map_uuid('location', o.warehouse_location_id);

ALTER TABLE inbound_order_items ADD COLUMN inbound_order_id_bigint BIGINT;

UPDATE inbound_order_items i
SET inbound_order_id_bigint = map_uuid('inbound_order', i.inbound_order_id);

ALTER TABLE receipts
    ADD COLUMN inbound_order_id_bigint BIGINT,
    ADD COLUMN received_by_user_id_bigint BIGINT;

UPDATE receipts r
SET inbound_order_id_bigint = map_uuid('inbound_order', r.inbound_order_id),
    received_by_user_id_bigint = map_uuid('user', r.received_by_user_id);

ALTER TABLE receipt_items ADD COLUMN receipt_id_bigint BIGINT;

UPDATE receipt_items ri
SET receipt_id_bigint = map_uuid('receipt', ri.receipt_id);

ALTER TABLE outbox_messages
    ADD COLUMN aggregate_id_bigint BIGINT;

UPDATE outbox_messages o
SET aggregate_id_bigint = CASE o.aggregate_type
    WHEN 'InboundOrder' THEN map_uuid('inbound_order', o.aggregate_id)
    WHEN 'Receipt'       THEN map_uuid('receipt', o.aggregate_id)
    ELSE map_uuid('inbound_order', o.aggregate_id)
END;

-- ---------------------------------------------------------------------------
-- Swap columns
-- ---------------------------------------------------------------------------
ALTER TABLE inbound_orders DROP CONSTRAINT pk_inbound_orders;
ALTER TABLE inbound_orders DROP COLUMN id;
ALTER TABLE inbound_orders RENAME COLUMN id_bigint TO id;
ALTER TABLE inbound_orders DROP COLUMN company_id;
ALTER TABLE inbound_orders DROP COLUMN warehouse_location_id;
ALTER TABLE inbound_orders RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE inbound_orders RENAME COLUMN warehouse_location_id_bigint TO warehouse_location_id;
ALTER TABLE inbound_orders ADD CONSTRAINT pk_inbound_orders PRIMARY KEY (id);

ALTER TABLE inbound_order_items DROP CONSTRAINT pk_inbound_order_items;
ALTER TABLE inbound_order_items DROP COLUMN id;
ALTER TABLE inbound_order_items RENAME COLUMN id_bigint TO id;
ALTER TABLE inbound_order_items DROP COLUMN inbound_order_id;
ALTER TABLE inbound_order_items RENAME COLUMN inbound_order_id_bigint TO inbound_order_id;
ALTER TABLE inbound_order_items ADD CONSTRAINT pk_inbound_order_items PRIMARY KEY (id);

ALTER TABLE receipts DROP CONSTRAINT pk_receipts;
ALTER TABLE receipts DROP COLUMN id;
ALTER TABLE receipts RENAME COLUMN id_bigint TO id;
ALTER TABLE receipts DROP COLUMN inbound_order_id;
ALTER TABLE receipts DROP COLUMN received_by_user_id;
ALTER TABLE receipts RENAME COLUMN inbound_order_id_bigint TO inbound_order_id;
ALTER TABLE receipts RENAME COLUMN received_by_user_id_bigint TO received_by_user_id;
ALTER TABLE receipts ADD CONSTRAINT pk_receipts PRIMARY KEY (id);

ALTER TABLE receipt_items DROP CONSTRAINT pk_receipt_items;
ALTER TABLE receipt_items DROP COLUMN id;
ALTER TABLE receipt_items RENAME COLUMN id_bigint TO id;
ALTER TABLE receipt_items DROP COLUMN receipt_id;
ALTER TABLE receipt_items RENAME COLUMN receipt_id_bigint TO receipt_id;
ALTER TABLE receipt_items ADD CONSTRAINT pk_receipt_items PRIMARY KEY (id);

ALTER TABLE outbox_messages DROP CONSTRAINT pk_outbox_messages;
ALTER TABLE outbox_messages DROP COLUMN id;
ALTER TABLE outbox_messages RENAME COLUMN id_bigint TO id;
ALTER TABLE outbox_messages DROP COLUMN aggregate_id;
ALTER TABLE outbox_messages RENAME COLUMN aggregate_id_bigint TO aggregate_id;
ALTER TABLE outbox_messages ADD CONSTRAINT pk_outbox_messages PRIMARY KEY (id);

-- ---------------------------------------------------------------------------
-- Recreate FK constraints
-- ---------------------------------------------------------------------------
ALTER TABLE inbound_order_items
    ADD CONSTRAINT fk_item_inbound_order
        FOREIGN KEY (inbound_order_id) REFERENCES inbound_orders (id) ON DELETE CASCADE;

ALTER TABLE receipts
    ADD CONSTRAINT fk_receipt_inbound_order
        FOREIGN KEY (inbound_order_id) REFERENCES inbound_orders (id);

ALTER TABLE receipt_items
    ADD CONSTRAINT fk_item_receipt
        FOREIGN KEY (receipt_id) REFERENCES receipts (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- BIGINT IDENTITY
-- ---------------------------------------------------------------------------
ALTER TABLE inbound_orders ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('inbound_orders', 'id'), COALESCE((SELECT MAX(id) FROM inbound_orders), 1));

ALTER TABLE inbound_order_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('inbound_order_items', 'id'), COALESCE((SELECT MAX(id) FROM inbound_order_items), 1));

ALTER TABLE receipts ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('receipts', 'id'), COALESCE((SELECT MAX(id) FROM receipts), 1));

ALTER TABLE receipt_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('receipt_items', 'id'), COALESCE((SELECT MAX(id) FROM receipt_items), 1));

ALTER TABLE outbox_messages ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('outbox_messages', 'id'), COALESCE((SELECT MAX(id) FROM outbox_messages), 1));

DROP FUNCTION map_uuid(VARCHAR, UUID);

COMMIT;
