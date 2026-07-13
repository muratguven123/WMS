-- =============================================================================
-- Flyway Migration: V1__Initial_Schema.sql
-- Description: Core tables for Sales Orders (OutboundOrder) and Picking lists
-- =============================================================================

-- 1. outbound_orders
CREATE TABLE outbound_orders
(
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_number        VARCHAR(100) NOT NULL,
    customer_id         UUID         NOT NULL,
    company_id          UUID         NOT NULL,
    order_date          TIMESTAMP    NOT NULL,
    status              VARCHAR(50)  NOT NULL,
    shipping_address_id UUID         NOT NULL,
    created_at          TIMESTAMP    NOT NULL,

    CONSTRAINT pk_outbound_orders PRIMARY KEY (id),
    CONSTRAINT uq_outbound_order_number UNIQUE (order_number)
);

CREATE INDEX idx_outbound_orders_customer_id ON outbound_orders (customer_id);
CREATE INDEX idx_outbound_orders_company_id ON outbound_orders (company_id);
CREATE INDEX idx_outbound_orders_order_number ON outbound_orders (order_number);

COMMENT ON TABLE  outbound_orders                     IS 'Müşteri siparişlerinin (Outbound Sales Order) üst başlık tablosu';
COMMENT ON COLUMN outbound_orders.id                  IS 'Benzersiz sipariş UUID';
COMMENT ON COLUMN outbound_orders.order_number        IS 'ERP veya Müşteri sipariş numarası (Unique)';
COMMENT ON COLUMN outbound_orders.customer_id         IS 'Müşteri UUID';
COMMENT ON COLUMN outbound_orders.company_id          IS 'Siparişin ait olduğu firma UUID';
COMMENT ON COLUMN outbound_orders.order_date          IS 'Sipariş tarihi';
COMMENT ON COLUMN outbound_orders.status              IS 'Sipariş durumu: PENDING, ALLOCATED, PICKING, PACKED, SHIPPED';
COMMENT ON COLUMN outbound_orders.shipping_address_id IS 'Sevkiyat adresi UUID';
COMMENT ON COLUMN outbound_orders.created_at          IS 'Kayıt oluşturulma tarihi (UTC)';

-- 2. outbound_order_items
CREATE TABLE outbound_order_items
(
    id                 UUID           NOT NULL DEFAULT gen_random_uuid(),
    outbound_order_id  UUID           NOT NULL,
    product_code       VARCHAR(100)   NOT NULL,
    quantity           NUMERIC(18, 4) NOT NULL,
    allocated_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    picked_quantity    NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,

    CONSTRAINT pk_outbound_order_items PRIMARY KEY (id),
    CONSTRAINT fk_item_outbound_order FOREIGN KEY (outbound_order_id) REFERENCES outbound_orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_outbound_order_items_order_id ON outbound_order_items (outbound_order_id);
CREATE INDEX idx_outbound_order_items_product ON outbound_order_items (product_code);

COMMENT ON TABLE  outbound_order_items                    IS 'Müşteri sipariş detay kalemleri tablosu';
COMMENT ON COLUMN outbound_order_items.id                 IS 'Benzersiz kalem UUID';
COMMENT ON COLUMN outbound_order_items.outbound_order_id  IS 'Sipariş üst başlık referansı';
COMMENT ON COLUMN outbound_order_items.product_code       IS 'Ürün kodu (SKU)';
COMMENT ON COLUMN outbound_order_items.quantity           IS 'Sipariş edilen toplam miktar';
COMMENT ON COLUMN outbound_order_items.allocated_quantity  IS 'Stoktan rezerve edilmiş (allocated) miktar';
COMMENT ON COLUMN outbound_order_items.picked_quantity     IS 'Toplanmış (picked) miktar';

-- 3. picking_lists
CREATE TABLE picking_lists
(
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    warehouse_location_id UUID         NOT NULL,
    created_by_user_id    UUID         NOT NULL,
    status                VARCHAR(50)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL,

    CONSTRAINT pk_picking_lists PRIMARY KEY (id)
);

CREATE INDEX idx_picking_lists_warehouse ON picking_lists (warehouse_location_id);
CREATE INDEX idx_picking_lists_status ON picking_lists (status);

COMMENT ON TABLE  picking_lists                       IS 'Depo içi toplama listesi (Picking Run) üst başlık tablosu';
COMMENT ON COLUMN picking_lists.id                    IS 'Benzersiz toplama listesi UUID';
COMMENT ON COLUMN picking_lists.warehouse_location_id IS 'Toplama işleminin yapılacağı depo lokasyon UUID';
COMMENT ON COLUMN picking_lists.created_by_user_id    IS 'Toplama listesini oluşturan kullanıcı UUID';
COMMENT ON COLUMN picking_lists.status                IS 'Liste durumu: PENDING, IN_PROGRESS, COMPLETED';
COMMENT ON COLUMN picking_lists.created_at            IS 'Liste oluşturulma tarihi (UTC)';

-- 4. picking_items
CREATE TABLE picking_items
(
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    picking_list_id         UUID           NOT NULL,
    outbound_order_item_id  UUID           NOT NULL,
    source_location_id      UUID           NOT NULL,
    address_code            VARCHAR(100)   NOT NULL,
    quantity_to_pick        NUMERIC(18, 4) NOT NULL,
    picked_quantity         NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    status                  VARCHAR(50)    NOT NULL,

    CONSTRAINT pk_picking_items PRIMARY KEY (id),
    CONSTRAINT fk_item_picking_list FOREIGN KEY (picking_list_id) REFERENCES picking_lists (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_picking_outbound FOREIGN KEY (outbound_order_item_id) REFERENCES outbound_order_items (id)
);

CREATE INDEX idx_picking_items_list_id ON picking_items (picking_list_id);
CREATE INDEX idx_picking_items_order_item_id ON picking_items (outbound_order_item_id);
CREATE INDEX idx_picking_items_source_location ON picking_items (source_location_id);

COMMENT ON TABLE  picking_items                         IS 'Depo içi toplama listesi detay kalemleri tablosu';
COMMENT ON COLUMN picking_items.id                      IS 'Benzersiz toplama kalemi UUID';
COMMENT ON COLUMN picking_items.picking_list_id         IS 'İlişkili toplama listesi UUID';
COMMENT ON COLUMN picking_items.outbound_order_item_id  IS 'İlişkili sipariş kalemi UUID';
COMMENT ON COLUMN picking_items.source_location_id      IS 'Stokun toplanacağı kaynak raf/lokasyon UUID';
COMMENT ON COLUMN picking_items.address_code            IS 'Toplama lokasyonu adresi (örn: A-12-03-01)';
COMMENT ON COLUMN picking_items.quantity_to_pick        IS 'Toplanması gereken miktar';
COMMENT ON COLUMN picking_items.picked_quantity         IS 'Toplanan miktar';
COMMENT ON COLUMN picking_items.status                  IS 'Kalem durumu: PENDING, PICKED, SHORTAGE';
