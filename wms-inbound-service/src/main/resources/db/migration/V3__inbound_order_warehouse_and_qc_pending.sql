-- =============================================================================
-- V3: Depo lokasyonu referansı ve QC PENDING desteği
-- =============================================================================

ALTER TABLE inbound_orders
    ADD COLUMN IF NOT EXISTS warehouse_location_id UUID;

COMMENT ON COLUMN inbound_orders.warehouse_location_id IS
    'Mal kabulün yapıldığı depo (Faz 1 Location) UUID — putaway motoru için kullanılır';
