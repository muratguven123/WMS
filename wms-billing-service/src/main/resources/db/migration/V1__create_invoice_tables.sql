-- =============================================================================
-- V1: Invoice, InvoiceItem, ExchangeDifferenceLog tabloları
-- =============================================================================
-- Kur Locking Politikası:
--   exchange_rate_value faturaya kopyalanır; döviz tanım tablosuna FK yok.
--   Bu sayede kur ilerleyen günlerde değişse bile fatura tutarları sabit kalır.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. invoices (fatura başlık)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoices
(
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    invoice_number         VARCHAR(64)  NOT NULL,
    customer_id            UUID         NOT NULL,
    location_id            UUID         NOT NULL,
    issue_date             TIMESTAMP    NOT NULL,

    -- Para birimi kodları (ISO 4217) — FK değil, sabit metin
    invoice_currency       CHAR(3)      NOT NULL,   -- Fatura para birimi  (EUR, USD …)
    accounting_currency    CHAR(3)      NOT NULL,   -- Muhasebe para birimi (TRY, EUR …)

    -- Dondurulmuş kur
    exchange_rate_date     DATE         NOT NULL,
    exchange_rate_value    NUMERIC(18, 6) NOT NULL, -- invoiceCurrency → accountingCurrency

    -- Tutarlar — işlem para birimi
    subtotal_original      NUMERIC(18, 4) NOT NULL,
    tax_amount_original    NUMERIC(18, 4) NOT NULL,
    grand_total_original   NUMERIC(18, 4) NOT NULL,

    -- Muhasebe para birimi toplamı
    grand_total_accounting NUMERIC(18, 4) NOT NULL,

    status                 VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT', 'APPROVED', 'SENT_TO_ERP', 'CANCELLED')),
    CONSTRAINT chk_invoices_exchange_rate CHECK (exchange_rate_value > 0),
    CONSTRAINT chk_invoices_subtotal CHECK (subtotal_original >= 0),
    CONSTRAINT chk_invoices_grand_total CHECK (grand_total_original >= 0)
);

COMMENT ON TABLE  invoices IS 'Fatura başlık tablosu. Kur, faturalama anında dondurularak saklanır.';
COMMENT ON COLUMN invoices.exchange_rate_value IS 'Faturalama anındaki dondurulmuş kur (invoiceCurrency → accountingCurrency). Döviz tanım tablosuna FK bağlantısı yoktur.';
COMMENT ON COLUMN invoices.invoice_currency    IS 'ISO 4217 fatura para birimi kodu (örn. EUR, USD).';
COMMENT ON COLUMN invoices.accounting_currency IS 'ISO 4217 muhasebe para birimi kodu (örn. TRY, EUR).';

-- Sık kullanılan sorgu patternleri için index
CREATE INDEX IF NOT EXISTS idx_invoices_customer_id ON invoices (customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_location_id ON invoices (location_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status       ON invoices (status);
CREATE INDEX IF NOT EXISTS idx_invoices_issue_date   ON invoices (issue_date);

-- ---------------------------------------------------------------------------
-- 2. invoice_items (fatura satırları)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoice_items
(
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    invoice_id          UUID          NOT NULL,
    item_description    VARCHAR(500)  NOT NULL,
    quantity            NUMERIC(18, 4) NOT NULL,
    unit_price_original NUMERIC(18, 4) NOT NULL,
    discount_original   NUMERIC(18, 4) NOT NULL DEFAULT 0,
    tax_rate            NUMERIC(5, 2)  NOT NULL,          -- Yüzde, örn. 20.00 = %20
    tax_amount_original NUMERIC(18, 4) NOT NULL,
    line_total_original NUMERIC(18, 4) NOT NULL,          -- (qty × unit_price) − discount (KDV hariç matrah)

    CONSTRAINT pk_invoice_items PRIMARY KEY (id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT chk_invoice_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_invoice_items_unit_price CHECK (unit_price_original >= 0),
    CONSTRAINT chk_invoice_items_discount CHECK (discount_original >= 0),
    CONSTRAINT chk_invoice_items_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100)
);

COMMENT ON TABLE  invoice_items IS 'Fatura satır kalemleri. Tüm tutarlar fatura para birimi cinsindendir.';
COMMENT ON COLUMN invoice_items.tax_rate IS 'Vergi oranı yüzde cinsinden (örn. 20.00 = %20). Azami 100.00.';

CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice_id ON invoice_items (invoice_id);

-- ---------------------------------------------------------------------------
-- 3. exchange_difference_logs (kur farkı kayıtları)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exchange_difference_logs
(
    id                         UUID          NOT NULL DEFAULT gen_random_uuid(),
    invoice_id                 UUID          NOT NULL,
    calculation_date           TIMESTAMP     NOT NULL,
    original_paid_amount       NUMERIC(18, 4) NOT NULL,  -- Ödeme anında fatura para birimindeki tutar
    rate_at_payment            NUMERIC(18, 6) NOT NULL,  -- Ödeme anındaki gerçek kur
    exchange_difference_amount NUMERIC(18, 4) NOT NULL,  -- +: kur kazancı / -: kur kaybı
    action_taken               VARCHAR(100)  NOT NULL,   -- Örn. POSTED_TO_ERP, MANUAL_ADJUSTMENT

    CONSTRAINT pk_exchange_difference_logs PRIMARY KEY (id),
    CONSTRAINT fk_exch_diff_logs_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT chk_exch_rate_at_payment CHECK (rate_at_payment > 0)
);

COMMENT ON TABLE  exchange_difference_logs IS 'Fatura kur farkı kayıtları. Dondurulmuş kur ile ödeme anındaki kur arasındaki farkı ve alınan aksiyonu saklar.';
COMMENT ON COLUMN exchange_difference_logs.exchange_difference_amount IS 'Pozitif = kur kazancı, Negatif = kur kaybı (muhasebe para birimi).';

CREATE INDEX IF NOT EXISTS idx_exch_diff_logs_invoice_id       ON exchange_difference_logs (invoice_id);
CREATE INDEX IF NOT EXISTS idx_exch_diff_logs_calculation_date ON exchange_difference_logs (calculation_date);
