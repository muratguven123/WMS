-- inbound_orders warehouse_location_id zorunlu
UPDATE inbound_orders SET warehouse_location_id = 1
WHERE warehouse_location_id IS NULL AND company_id = 1;

UPDATE inbound_orders SET warehouse_location_id = 2
WHERE warehouse_location_id IS NULL AND company_id = 2;

UPDATE inbound_orders SET warehouse_location_id = 1
WHERE warehouse_location_id IS NULL;

ALTER TABLE inbound_orders
    ALTER COLUMN warehouse_location_id SET NOT NULL;
