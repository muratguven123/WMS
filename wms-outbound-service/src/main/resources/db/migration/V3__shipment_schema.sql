-- =============================================================================
-- Flyway Migration: V3__shipment_schema.sql
-- Description: Tables for Shipment and ShipmentItem management
-- =============================================================================

-- 1. shipments
CREATE TABLE shipments
(
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    shipment_number VARCHAR(100) NOT NULL,
    company_id      UUID         NOT NULL,
    carrier_code    VARCHAR(100),
    tracking_number VARCHAR(100),
    status          VARCHAR(50)  NOT NULL,
    total_boxes     INT          NOT NULL DEFAULT 0,
    total_weight    NUMERIC(10, 4),
    dispatched_at   TIMESTAMP,

    CONSTRAINT pk_shipments PRIMARY KEY (id),
    CONSTRAINT uq_shipment_number UNIQUE (shipment_number)
);

CREATE INDEX idx_shipments_company_id ON shipments (company_id);
CREATE INDEX idx_shipments_shipment_number ON shipments (shipment_number);

COMMENT ON TABLE  shipments                 IS 'Sevkiyat ve çıkış işlemlerinin (Shipment) üst başlık tablosu';
COMMENT ON COLUMN shipments.id              IS 'Benzersiz sevkiyat UUID';
COMMENT ON COLUMN shipments.shipment_number IS 'Sevkiyat veya çıkış numarası (Unique)';
COMMENT ON COLUMN shipments.company_id      IS 'Siparişin ait olduğu firma UUID';
COMMENT ON COLUMN shipments.carrier_code    IS 'Kargo/Taşıyıcı kodu (örn: DHL, YURTICI)';
COMMENT ON COLUMN shipments.tracking_number IS 'Kargo/Taşıyıcı takip numarası';
COMMENT ON COLUMN shipments.status          IS 'Sevkiyat durumu: PENDING, LOADED, DISPATCHED';
COMMENT ON COLUMN shipments.total_boxes     IS 'Toplam koli sayısı';
COMMENT ON COLUMN shipments.total_weight    IS 'Toplam ağırlık (kg/dese)';
COMMENT ON COLUMN shipments.dispatched_at   IS 'Sevkiyatın çıkış/gönderim tarihi ve saati';

-- 2. shipment_items
CREATE TABLE shipment_items
(
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    shipment_id       UUID         NOT NULL,
    outbound_order_id UUID         NOT NULL,
    box_sscc_number   VARCHAR(100) NOT NULL,
    status            VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_shipment_items PRIMARY KEY (id),
    CONSTRAINT fk_shipment_item_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE
);

CREATE INDEX idx_shipment_items_shipment_id ON shipment_items (shipment_id);
CREATE INDEX idx_shipment_items_outbound_order_id ON shipment_items (outbound_order_id);
CREATE INDEX idx_shipment_items_box_sscc_number ON shipment_items (box_sscc_number);

COMMENT ON TABLE  shipment_items                   IS 'Sevkiyat detay kalemleri ve palet/koli eşleşmeleri tablosu';
COMMENT ON COLUMN shipment_items.id                 IS 'Benzersiz sevkiyat kalemi UUID';
COMMENT ON COLUMN shipment_items.shipment_id        IS 'İlişkili sevkiyat (shipments.id) referansı';
COMMENT ON COLUMN shipment_items.outbound_order_id  IS 'İlişkili çıkış siparişi UUID';
COMMENT ON COLUMN shipment_items.box_sscc_number    IS 'Koli SSCC barkodu';
COMMENT ON COLUMN shipment_items.status            IS 'Koli yükleme durumu: STAGED, LOADED';
