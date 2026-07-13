-- =============================================================================
-- V6 — UUID → BIGINT migration (wms_outbound_db)
-- Requires wms_core_db V16 and wms_finance_db V11 to have run first.
-- Address refs are read directly from wms_localization_db.localization.address.
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

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT entity_type, old_uuid, new_id
FROM dblink(
    'host=localhost dbname=wms_finance_db user=postgres password=postgres',
    'SELECT entity_type, old_uuid, new_id FROM finance.id_legacy_map WHERE entity_type = ''customer'''
) AS t(entity_type VARCHAR(50), old_uuid UUID, new_id BIGINT)
ON CONFLICT DO NOTHING;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT entity_type, old_uuid, new_id
FROM dblink(
    'host=localhost dbname=wms_localization_db user=postgres password=postgres',
    'SELECT entity_type, old_uuid, new_id FROM public.id_legacy_map WHERE entity_type = ''address'''
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
ALTER TABLE outbound_order_items DROP CONSTRAINT IF EXISTS fk_item_outbound_order;
ALTER TABLE picking_items DROP CONSTRAINT IF EXISTS fk_item_picking_list;
ALTER TABLE picking_items DROP CONSTRAINT IF EXISTS fk_item_picking_outbound;
ALTER TABLE shipment_items DROP CONSTRAINT IF EXISTS fk_shipment_item_shipment;

-- ---------------------------------------------------------------------------
-- Migrate PKs (parent first)
-- ---------------------------------------------------------------------------
ALTER TABLE outbound_orders ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM outbound_orders
)
UPDATE outbound_orders o
SET id_bigint = r.rn
FROM ranked r
WHERE o.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'outbound_order', id, id_bigint FROM outbound_orders ON CONFLICT DO NOTHING;

ALTER TABLE outbound_order_items ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY outbound_order_id, id) AS rn
    FROM outbound_order_items
)
UPDATE outbound_order_items o
SET id_bigint = r.rn
FROM ranked r
WHERE o.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'outbound_order_item', id, id_bigint FROM outbound_order_items ON CONFLICT DO NOTHING;

ALTER TABLE picking_lists ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM picking_lists
)
UPDATE picking_lists p
SET id_bigint = r.rn
FROM ranked r
WHERE p.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'picking_list', id, id_bigint FROM picking_lists ON CONFLICT DO NOTHING;

ALTER TABLE picking_items ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY picking_list_id, id) AS rn
    FROM picking_items
)
UPDATE picking_items p
SET id_bigint = r.rn
FROM ranked r
WHERE p.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'picking_item', id, id_bigint FROM picking_items ON CONFLICT DO NOTHING;

ALTER TABLE shipments ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY dispatched_at NULLS LAST, id) AS rn
    FROM shipments
)
UPDATE shipments s
SET id_bigint = r.rn
FROM ranked r
WHERE s.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'shipment', id, id_bigint FROM shipments ON CONFLICT DO NOTHING;

ALTER TABLE shipment_items ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY shipment_id, id) AS rn
    FROM shipment_items
)
UPDATE shipment_items s
SET id_bigint = r.rn
FROM ranked r
WHERE s.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'shipment_item', id, id_bigint FROM shipment_items ON CONFLICT DO NOTHING;

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
SELECT 'outbox_message', id, id_bigint FROM outbox_messages ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Migrate FK / cross-service columns
-- ---------------------------------------------------------------------------
ALTER TABLE outbound_orders
    ADD COLUMN customer_id_bigint BIGINT,
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN shipping_address_id_bigint BIGINT;

UPDATE outbound_orders o
SET customer_id_bigint = map_uuid('customer', o.customer_id),
    company_id_bigint = map_uuid('company', o.company_id),
    shipping_address_id_bigint = map_uuid('address', o.shipping_address_id);

ALTER TABLE outbound_order_items ADD COLUMN outbound_order_id_bigint BIGINT;

UPDATE outbound_order_items i
SET outbound_order_id_bigint = map_uuid('outbound_order', i.outbound_order_id);

ALTER TABLE picking_lists
    ADD COLUMN warehouse_location_id_bigint BIGINT,
    ADD COLUMN created_by_user_id_bigint BIGINT,
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN assigned_user_id_bigint BIGINT;

UPDATE picking_lists p
SET warehouse_location_id_bigint = map_uuid('location', p.warehouse_location_id),
    created_by_user_id_bigint = map_uuid('user', p.created_by_user_id),
    company_id_bigint = map_uuid('company', p.company_id),
    assigned_user_id_bigint = map_uuid('user', p.assigned_user_id);

ALTER TABLE picking_items
    ADD COLUMN picking_list_id_bigint BIGINT,
    ADD COLUMN outbound_order_item_id_bigint BIGINT,
    ADD COLUMN source_location_id_bigint BIGINT;

UPDATE picking_items p
SET picking_list_id_bigint = map_uuid('picking_list', p.picking_list_id),
    outbound_order_item_id_bigint = map_uuid('outbound_order_item', p.outbound_order_item_id),
    source_location_id_bigint = map_uuid('storage_location', p.source_location_id);

ALTER TABLE shipments
    ADD COLUMN company_id_bigint BIGINT,
    ADD COLUMN warehouse_location_id_bigint BIGINT;

UPDATE shipments s
SET company_id_bigint = map_uuid('company', s.company_id),
    warehouse_location_id_bigint = map_uuid('location', s.warehouse_location_id);

ALTER TABLE shipment_items
    ADD COLUMN shipment_id_bigint BIGINT,
    ADD COLUMN outbound_order_id_bigint BIGINT;

UPDATE shipment_items s
SET shipment_id_bigint = map_uuid('shipment', s.shipment_id),
    outbound_order_id_bigint = map_uuid('outbound_order', s.outbound_order_id);

ALTER TABLE outbox_messages ADD COLUMN aggregate_id_bigint BIGINT;

UPDATE outbox_messages o
SET aggregate_id_bigint = CASE o.aggregate_type
    WHEN 'OutboundOrder' THEN map_uuid('outbound_order', o.aggregate_id)
    WHEN 'Shipment'      THEN map_uuid('shipment', o.aggregate_id)
    WHEN 'PickingList'   THEN map_uuid('picking_list', o.aggregate_id)
    ELSE map_uuid('outbound_order', o.aggregate_id)
END;

-- ---------------------------------------------------------------------------
-- Swap columns
-- ---------------------------------------------------------------------------
ALTER TABLE outbound_orders DROP CONSTRAINT pk_outbound_orders;
ALTER TABLE outbound_orders DROP COLUMN id;
ALTER TABLE outbound_orders RENAME COLUMN id_bigint TO id;
ALTER TABLE outbound_orders
    DROP COLUMN customer_id, DROP COLUMN company_id, DROP COLUMN shipping_address_id;
ALTER TABLE outbound_orders RENAME COLUMN customer_id_bigint TO customer_id;
ALTER TABLE outbound_orders RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE outbound_orders RENAME COLUMN shipping_address_id_bigint TO shipping_address_id;
ALTER TABLE outbound_orders ADD CONSTRAINT pk_outbound_orders PRIMARY KEY (id);

ALTER TABLE outbound_order_items DROP CONSTRAINT pk_outbound_order_items;
ALTER TABLE outbound_order_items DROP COLUMN id;
ALTER TABLE outbound_order_items RENAME COLUMN id_bigint TO id;
ALTER TABLE outbound_order_items DROP COLUMN outbound_order_id;
ALTER TABLE outbound_order_items RENAME COLUMN outbound_order_id_bigint TO outbound_order_id;
ALTER TABLE outbound_order_items ADD CONSTRAINT pk_outbound_order_items PRIMARY KEY (id);

ALTER TABLE picking_lists DROP CONSTRAINT pk_picking_lists;
ALTER TABLE picking_lists DROP COLUMN id;
ALTER TABLE picking_lists RENAME COLUMN id_bigint TO id;
ALTER TABLE picking_lists
    DROP COLUMN warehouse_location_id,
    DROP COLUMN created_by_user_id,
    DROP COLUMN company_id,
    DROP COLUMN assigned_user_id;
ALTER TABLE picking_lists RENAME COLUMN warehouse_location_id_bigint TO warehouse_location_id;
ALTER TABLE picking_lists RENAME COLUMN created_by_user_id_bigint TO created_by_user_id;
ALTER TABLE picking_lists RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE picking_lists RENAME COLUMN assigned_user_id_bigint TO assigned_user_id;
ALTER TABLE picking_lists ADD CONSTRAINT pk_picking_lists PRIMARY KEY (id);

ALTER TABLE picking_items DROP CONSTRAINT pk_picking_items;
ALTER TABLE picking_items DROP COLUMN id;
ALTER TABLE picking_items RENAME COLUMN id_bigint TO id;
ALTER TABLE picking_items
    DROP COLUMN picking_list_id,
    DROP COLUMN outbound_order_item_id,
    DROP COLUMN source_location_id;
ALTER TABLE picking_items RENAME COLUMN picking_list_id_bigint TO picking_list_id;
ALTER TABLE picking_items RENAME COLUMN outbound_order_item_id_bigint TO outbound_order_item_id;
ALTER TABLE picking_items RENAME COLUMN source_location_id_bigint TO source_location_id;
ALTER TABLE picking_items ADD CONSTRAINT pk_picking_items PRIMARY KEY (id);

ALTER TABLE shipments DROP CONSTRAINT pk_shipments;
ALTER TABLE shipments DROP COLUMN id;
ALTER TABLE shipments RENAME COLUMN id_bigint TO id;
ALTER TABLE shipments DROP COLUMN company_id, DROP COLUMN warehouse_location_id;
ALTER TABLE shipments RENAME COLUMN company_id_bigint TO company_id;
ALTER TABLE shipments RENAME COLUMN warehouse_location_id_bigint TO warehouse_location_id;
ALTER TABLE shipments ADD CONSTRAINT pk_shipments PRIMARY KEY (id);

ALTER TABLE shipment_items DROP CONSTRAINT pk_shipment_items;
ALTER TABLE shipment_items DROP COLUMN id;
ALTER TABLE shipment_items RENAME COLUMN id_bigint TO id;
ALTER TABLE shipment_items DROP COLUMN shipment_id, DROP COLUMN outbound_order_id;
ALTER TABLE shipment_items RENAME COLUMN shipment_id_bigint TO shipment_id;
ALTER TABLE shipment_items RENAME COLUMN outbound_order_id_bigint TO outbound_order_id;
ALTER TABLE shipment_items ADD CONSTRAINT pk_shipment_items PRIMARY KEY (id);

ALTER TABLE outbox_messages DROP CONSTRAINT pk_outbox_messages;
ALTER TABLE outbox_messages DROP COLUMN id;
ALTER TABLE outbox_messages RENAME COLUMN id_bigint TO id;
ALTER TABLE outbox_messages DROP COLUMN aggregate_id;
ALTER TABLE outbox_messages RENAME COLUMN aggregate_id_bigint TO aggregate_id;
ALTER TABLE outbox_messages ADD CONSTRAINT pk_outbox_messages PRIMARY KEY (id);

-- ---------------------------------------------------------------------------
-- Recreate FK constraints
-- ---------------------------------------------------------------------------
ALTER TABLE outbound_order_items
    ADD CONSTRAINT fk_item_outbound_order
        FOREIGN KEY (outbound_order_id) REFERENCES outbound_orders (id) ON DELETE CASCADE;

ALTER TABLE picking_items
    ADD CONSTRAINT fk_item_picking_list
        FOREIGN KEY (picking_list_id) REFERENCES picking_lists (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_item_picking_outbound
        FOREIGN KEY (outbound_order_item_id) REFERENCES outbound_order_items (id);

ALTER TABLE shipment_items
    ADD CONSTRAINT fk_shipment_item_shipment
        FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- BIGINT IDENTITY
-- ---------------------------------------------------------------------------
ALTER TABLE outbound_orders ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('outbound_orders', 'id'), COALESCE((SELECT MAX(id) FROM outbound_orders), 1));

ALTER TABLE outbound_order_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('outbound_order_items', 'id'), COALESCE((SELECT MAX(id) FROM outbound_order_items), 1));

ALTER TABLE picking_lists ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('picking_lists', 'id'), COALESCE((SELECT MAX(id) FROM picking_lists), 1));

ALTER TABLE picking_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('picking_items', 'id'), COALESCE((SELECT MAX(id) FROM picking_items), 1));

ALTER TABLE shipments ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('shipments', 'id'), COALESCE((SELECT MAX(id) FROM shipments), 1));

ALTER TABLE shipment_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('shipment_items', 'id'), COALESCE((SELECT MAX(id) FROM shipment_items), 1));

ALTER TABLE outbox_messages ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('outbox_messages', 'id'), COALESCE((SELECT MAX(id) FROM outbox_messages), 1));

DROP FUNCTION map_uuid(VARCHAR, UUID);

COMMIT;
