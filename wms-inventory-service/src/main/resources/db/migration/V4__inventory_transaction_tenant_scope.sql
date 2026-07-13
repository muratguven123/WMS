-- inventory_transactions tenant scope
ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS company_id BIGINT,
    ADD COLUMN IF NOT EXISTS warehouse_location_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_inv_tx_company_warehouse
    ON inventory_transactions (company_id, warehouse_location_id);

-- Backfill from inventories where possible (source bin)
UPDATE inventory_transactions it
SET warehouse_location_id = i.warehouse_location_id,
    company_id = i.company_id
FROM inventories i
WHERE it.source_location_id = i.storage_location_id
  AND it.warehouse_location_id IS NULL;
