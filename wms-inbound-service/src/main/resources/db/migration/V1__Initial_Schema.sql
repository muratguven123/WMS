-- =============================================================================
-- Flyway Migration: V1__Initial_Schema.sql
-- Description: Core tables for purchase orders (InboundOrder) and receipts
-- =============================================================================

-- 1. inbound_orders
CREATE TABLE inbound_orders
(
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_number  VARCHAR(100) NOT NULL,
    company_id    UUID         NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    order_date    TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_inbound_orders PRIMARY KEY (id),
    CONSTRAINT uq_inbound_order_number UNIQUE (order_number)
);

CREATE INDEX idx_inbound_orders_company_id ON inbound_orders (company_id);
CREATE INDEX idx_inbound_orders_order_number ON inbound_orders (order_number);

COMMENT ON TABLE  inbound_orders               IS 'Satın alma siparişlerinin (Purchase Order) üst başlık tablosu';
COMMENT ON COLUMN inbound_orders.id            IS 'Benzersiz sipariş UUID';
COMMENT ON COLUMN inbound_orders.order_number  IS 'ERP sisteminden gelen benzersiz sipariş numarası';
COMMENT ON COLUMN inbound_orders.company_id    IS 'Siparişin ait olduğu firma UUID';
COMMENT ON COLUMN inbound_orders.supplier_name IS 'Tedarikçi firma adı';
COMMENT ON COLUMN inbound_orders.order_date    IS 'Sipariş oluşturma tarihi (ERP)';
COMMENT ON COLUMN inbound_orders.status        IS 'Sipariş durumu: PENDING, RECEIVING, COMPLETED, CANCELLED';
COMMENT ON COLUMN inbound_orders.created_at    IS 'Kayıt oluşturulma tarihi (UTC)';

-- 2. inbound_order_items
CREATE TABLE inbound_order_items
(
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    inbound_order_id  UUID           NOT NULL,
    product_code      VARCHAR(100)   NOT NULL,
    quantity          NUMERIC(18, 4) NOT NULL,
    received_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    uom               VARCHAR(50)    NOT NULL,
    unit_volume       NUMERIC(18, 4) NOT NULL,
    unit_weight       NUMERIC(18, 4) NOT NULL,

    CONSTRAINT pk_inbound_order_items PRIMARY KEY (id),
    CONSTRAINT fk_item_inbound_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_inbound_order_items_order_id ON inbound_order_items (inbound_order_id);
CREATE INDEX idx_inbound_order_items_product ON inbound_order_items (product_code);

COMMENT ON TABLE  inbound_order_items                   IS 'Satın alma siparişi detay kalemleri tablosu';
COMMENT ON COLUMN inbound_order_items.id                IS 'Benzersiz kalem UUID';
COMMENT ON COLUMN inbound_order_items.inbound_order_id  IS 'Sipariş üst başlık referansı';
COMMENT ON COLUMN inbound_order_items.product_code      IS 'Ürün kodu (SKU)';
COMMENT ON COLUMN inbound_order_items.quantity          IS 'Sipariş edilen toplam miktar';
COMMENT ON COLUMN inbound_order_items.received_quantity IS 'Mal kabulü yapılmış miktar';
COMMENT ON COLUMN inbound_order_items.uom               IS 'Ölçü birimi (Adet, KG vb.)';
COMMENT ON COLUMN inbound_order_items.unit_volume       IS 'Ürünün birim hacmi (m3)';
COMMENT ON COLUMN inbound_order_items.unit_weight       IS 'Ürünün birim ağırlığı (kg)';

-- 3. receipts
CREATE TABLE receipts
(
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    inbound_order_id    UUID         NOT NULL,
    receipt_number      VARCHAR(100) NOT NULL,
    received_by_user_id UUID         NOT NULL,
    received_at         TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_receipts PRIMARY KEY (id),
    CONSTRAINT uq_receipt_number UNIQUE (receipt_number),
    CONSTRAINT fk_receipt_inbound_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_orders (id)
);

CREATE INDEX idx_receipts_inbound_order_id ON receipts (inbound_order_id);
CREATE INDEX idx_receipts_receipt_number ON receipts (receipt_number);

COMMENT ON TABLE  receipts                     IS 'Mal kabul (Goods Receipt / İrsaliye) başlık tablosu';
COMMENT ON COLUMN receipts.id                  IS 'Benzersiz mal kabul UUID';
COMMENT ON COLUMN receipts.inbound_order_id    IS 'İlişkili satın alma siparişi UUID';
COMMENT ON COLUMN receipts.receipt_number      IS 'Benzersiz irsaliye/kabul numarası';
COMMENT ON COLUMN receipts.received_by_user_id IS 'Mal kabulü yapan kullanıcı (User) UUID';
COMMENT ON COLUMN receipts.received_at         IS 'Mal kabulün gerçekleştiği tarih/saat';
COMMENT ON COLUMN receipts.status              IS 'Kabul kalite kontrol durumu: QC_PENDING, APPROVED, REJECTED';

-- 4. receipt_items
CREATE TABLE receipt_items
(
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    receipt_id    UUID           NOT NULL,
    product_code  VARCHAR(100)   NOT NULL,
    quantity      NUMERIC(18, 4) NOT NULL,
    lot_number    VARCHAR(100),
    serial_number VARCHAR(100),
    qc_status     VARCHAR(50)    NOT NULL,

    CONSTRAINT pk_receipt_items PRIMARY KEY (id),
    CONSTRAINT fk_item_receipt FOREIGN KEY (receipt_id) REFERENCES receipts (id) ON DELETE CASCADE
);

CREATE INDEX idx_receipt_items_receipt_id ON receipt_items (receipt_id);
CREATE INDEX idx_receipt_items_product ON receipt_items (product_code);

COMMENT ON TABLE  receipt_items               IS 'Mal kabul detay kalemleri tablosu';
COMMENT ON COLUMN receipt_items.id            IS 'Benzersiz mal kabul kalem UUID';
COMMENT ON COLUMN receipt_items.receipt_id    IS 'İlişkili mal kabul başlık UUID';
COMMENT ON COLUMN receipt_items.product_code  IS 'Kabul edilen ürün kodu';
COMMENT ON COLUMN receipt_items.quantity      IS 'Kabul edilen miktar';
COMMENT ON COLUMN receipt_items.lot_number    IS 'Ürün parti/lot numarası (izlenebilirlik)';
COMMENT ON COLUMN receipt_items.serial_number IS 'Ürün seri numarası (varsa)';
COMMENT ON COLUMN receipt_items.qc_status     IS 'Kalite kontrol sonucu: PASSED, FAILED';
