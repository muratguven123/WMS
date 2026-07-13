-- =============================================================================
-- Flyway Migration: V1__Initial_Schema.sql
-- Description: Core tables for stock tracking (Inventory) and movement history (InventoryTransaction)
-- =============================================================================

-- 1. inventories
CREATE TABLE inventories
(
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    product_code          VARCHAR(100)   NOT NULL,
    storage_location_id   UUID           NOT NULL,
    quantity              NUMERIC(18, 4) NOT NULL,
    lot_number            VARCHAR(100),
    serial_number         VARCHAR(100),
    status                VARCHAR(50)    NOT NULL,
    expiry_date           DATE,
    company_id            UUID           NOT NULL,
    warehouse_location_id UUID           NOT NULL,
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_inventories PRIMARY KEY (id),
    CONSTRAINT uq_inventory_location_product_lot_serial UNIQUE (storage_location_id, product_code, lot_number, serial_number)
);

CREATE INDEX idx_inventories_product_code ON inventories (product_code);
CREATE INDEX idx_inventories_storage_location ON inventories (storage_location_id);
CREATE INDEX idx_inventories_warehouse_location ON inventories (warehouse_location_id);
CREATE INDEX idx_inventories_company_id ON inventories (company_id);

COMMENT ON TABLE  inventories                        IS 'Depodaki anlık stok (on-hand) durumunu tutan envanter tablosu';
COMMENT ON COLUMN inventories.id                     IS 'Benzersiz stok UUID';
COMMENT ON COLUMN inventories.product_code           IS 'Ürün kodu (SKU)';
COMMENT ON COLUMN inventories.storage_location_id    IS 'Stokun bulunduğu depo gözü/raf (StorageLocation) UUID';
COMMENT ON COLUMN inventories.quantity               IS 'Stok miktarı';
COMMENT ON COLUMN inventories.lot_number             IS 'Ürün parti/lot numarası (izlenebilirlik)';
COMMENT ON COLUMN inventories.serial_number          IS 'Ürün seri numarası (varsa)';
COMMENT ON COLUMN inventories.status                 IS 'Stok durumu: AVAILABLE, BLOCKED, ALLOCATED';
COMMENT ON COLUMN inventories.expiry_date            IS 'Son kullanma tarihi (FEFO için)';
COMMENT ON COLUMN inventories.company_id             IS 'Stok sahibi firma UUID';
COMMENT ON COLUMN inventories.warehouse_location_id  IS 'Stokun bulunduğu ana depo (Warehouse) UUID';
COMMENT ON COLUMN inventories.updated_at             IS 'Son stok güncelleme zamanı (FIFO için)';


-- 2. inventory_transactions
CREATE TABLE inventory_transactions
(
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    transaction_type      VARCHAR(50)    NOT NULL,
    source_location_id    UUID,
    target_location_id    UUID,
    product_code          VARCHAR(100)   NOT NULL,
    quantity              NUMERIC(18, 4) NOT NULL,
    lot_number            VARCHAR(100),
    serial_number         VARCHAR(100),
    transaction_date      TIMESTAMP      NOT NULL DEFAULT NOW(),
    performed_by_user_id  UUID           NOT NULL,

    CONSTRAINT pk_inventory_transactions PRIMARY KEY (id)
);

-- Indexes for performance
CREATE INDEX idx_inv_tx_product_code ON inventory_transactions (product_code);
CREATE INDEX idx_inv_tx_source_loc ON inventory_transactions (source_location_id);
CREATE INDEX idx_inv_tx_target_loc ON inventory_transactions (target_location_id);
CREATE INDEX idx_inv_tx_date ON inventory_transactions (transaction_date);

COMMENT ON TABLE  inventory_transactions                      IS 'Stok hareket geçmişini (envanter hareketleri) tutan tablo';
COMMENT ON COLUMN inventory_transactions.id                   IS 'Benzersiz hareket UUID';
COMMENT ON COLUMN inventory_transactions.transaction_type     IS 'Stok hareket tipi: PUTAWAY, INTERNAL_MOVE, PICKING, ADJUSTMENT';
COMMENT ON COLUMN inventory_transactions.source_location_id   IS 'Kaynak lokasyon (çıkış yapılan raf) UUID';
COMMENT ON COLUMN inventory_transactions.target_location_id   IS 'Hedef lokasyon (giriş yapılan raf) UUID';
COMMENT ON COLUMN inventory_transactions.product_code         IS 'Hareket gören ürün kodu';
COMMENT ON COLUMN inventory_transactions.quantity             IS 'Hareket miktarı';
COMMENT ON COLUMN inventory_transactions.lot_number           IS 'Ürün parti/lot numarası';
COMMENT ON COLUMN inventory_transactions.serial_number        IS 'Ürün seri numarası';
COMMENT ON COLUMN inventory_transactions.transaction_date     IS 'Hareket gerçekleşme zamanı';
COMMENT ON COLUMN inventory_transactions.performed_by_user_id IS 'Hareketi gerçekleştiren operatör/kullanıcı UUID';
