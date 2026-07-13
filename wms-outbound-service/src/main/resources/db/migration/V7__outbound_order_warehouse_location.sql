-- outbound_orders depo kapsamı
ALTER TABLE outbound_orders
    ADD COLUMN IF NOT EXISTS warehouse_location_id BIGINT;

UPDATE outbound_orders SET warehouse_location_id = 1
WHERE warehouse_location_id IS NULL AND company_id = 1;

UPDATE outbound_orders SET warehouse_location_id = 2
WHERE warehouse_location_id IS NULL AND company_id = 2;

UPDATE outbound_orders SET warehouse_location_id = 1
WHERE warehouse_location_id IS NULL;

ALTER TABLE outbound_orders
    ALTER COLUMN warehouse_location_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbound_order_warehouse
    ON outbound_orders (company_id, warehouse_location_id);
