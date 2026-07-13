-- =====================================================================
-- V5: Tenant-scoped operasyonel veri — Stock örneği (Prompt 1.3)
-- =====================================================================

CREATE TABLE stocks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID             NOT NULL,
    location_id UUID             NOT NULL,
    sku         VARCHAR(100)     NOT NULL,
    quantity    INTEGER          NOT NULL DEFAULT 0,
    is_active   BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT fk_stock_company
        FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_stock_location
        FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT uk_stock_location_sku
        UNIQUE (location_id, sku),
    CONSTRAINT chk_stock_quantity_nonneg
        CHECK (quantity >= 0)
);

CREATE INDEX idx_stock_company  ON stocks (company_id);
CREATE INDEX idx_stock_location ON stocks (location_id);
