-- =============================================================================
-- Flyway Migration: V2__add_status_to_unique_constraint.sql
-- Description: Update unique constraint of inventories table to include status
-- =============================================================================

ALTER TABLE inventories DROP CONSTRAINT uq_inventory_location_product_lot_serial;

ALTER TABLE inventories ADD CONSTRAINT uq_inventory_location_product_lot_serial UNIQUE (storage_location_id, product_code, lot_number, serial_number, status);
