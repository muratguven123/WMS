-- Adds warehouse location reference for inventory issue during dispatch
ALTER TABLE shipments
    ADD COLUMN warehouse_location_id UUID;

COMMENT ON COLUMN shipments.warehouse_location_id IS 'Stok düşümü için depo lokasyon UUID';
