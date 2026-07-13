-- =============================================================================
-- V9 — Vergi Hesaplama Denetim Logu (Prompt 15.2)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Şema
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS finance;

-- -----------------------------------------------------------------------------
-- 1. tax_type — Sistemde tanımlı vergi tipleri
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS finance.tax_type
(
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    code         VARCHAR(50)  NOT NULL,
    description  VARCHAR(200) NOT NULL,
    default_rate NUMERIC(5, 2) NOT NULL
        CONSTRAINT chk_tax_type_audit_rate CHECK (default_rate >= 0 AND default_rate <= 100),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_tax_type_audit PRIMARY KEY (id),
    CONSTRAINT uq_tax_type_audit_code UNIQUE (code)
);

COMMENT ON TABLE finance.tax_type IS 'Sistemde kullanılan vergi tiplerini tanımlar (KDV, ÖTV, Stopaj vb.).';
COMMENT ON COLUMN finance.tax_type.code IS 'İnsan okunabilir benzersiz kod (örn: KDV_20, OTV_25).';
COMMENT ON COLUMN finance.tax_type.default_rate IS 'Varsayılan oran. Log kaydı oluşturulurken buradan kopyalanır.';

-- updated_at otomatik güncelleme trigger'ı
CREATE OR REPLACE FUNCTION finance.set_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tax_type_updated_at
    BEFORE UPDATE ON finance.tax_type
    FOR EACH ROW
EXECUTE FUNCTION finance.set_updated_at();

-- -----------------------------------------------------------------------------
-- 2. tax_calculation_log — Her vergi hesaplama işleminin denetim kaydı
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS finance.tax_calculation_log
(
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),

    -- İşlem Referansı
    transaction_type         VARCHAR(50)   NOT NULL,
    transaction_reference_id UUID          NOT NULL,

    -- Vergi Tipi FK
    tax_type_id              UUID          NOT NULL,

    -- Hesaplama Değerleri
    tax_rate                 NUMERIC(5, 2)  NOT NULL
        CONSTRAINT chk_log_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100),
    tax_base_amount          NUMERIC(18, 4) NOT NULL
        CONSTRAINT chk_log_base_amount CHECK (tax_base_amount >= 0),
    calculated_tax_amount    NUMERIC(18, 4) NOT NULL
        CONSTRAINT chk_log_tax_amount CHECK (calculated_tax_amount >= 0),

    -- Meta Veriler
    is_inclusive             BOOLEAN        NOT NULL,
    is_exempt                BOOLEAN        NOT NULL,
    exemption_code           VARCHAR(100),
    calculation_source       VARCHAR(100)   NOT NULL,
    calculation_date         TIMESTAMPTZ    NOT NULL DEFAULT now(),

    -- Muafiyet Tutarlılık Kısıtları
    -- Muafiyet varsa exemption_code dolu ve calculated_tax_amount sıfır olmalı
    CONSTRAINT chk_log_exempt_code
        CHECK (
            (is_exempt = TRUE AND exemption_code IS NOT NULL)
                OR
            (is_exempt = FALSE AND exemption_code IS NULL)
            ),
    CONSTRAINT chk_log_exempt_zero_tax
        CHECK (
            is_exempt = FALSE
                OR
            calculated_tax_amount = 0
            ),

    CONSTRAINT pk_tax_calculation_log PRIMARY KEY (id),
    CONSTRAINT fk_tax_log_tax_type
        FOREIGN KEY (tax_type_id) REFERENCES finance.tax_type (id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT
);

COMMENT ON TABLE finance.tax_calculation_log IS
    'Her vergi hesaplama işleminin denetime uygun (audit-ready) değişmez kaydı. '
    'Kayıtlar güncellenmez; hata düzeltmesi için ters kayıt (reversal) oluşturulur.';
COMMENT ON COLUMN finance.tax_calculation_log.transaction_type IS
    'İlgili işlem tipi (INVOICE_LINE, TRANSACTION_FEE vb.). Java enum değeriyle eşleşir.';
COMMENT ON COLUMN finance.tax_calculation_log.transaction_reference_id IS
    'İlgili iş nesnesinin PK''sı. Cross-module bağımsızlığı için DB-level FK tanımlı değildir.';
COMMENT ON COLUMN finance.tax_calculation_log.tax_rate IS
    'Hesaplama anındaki oran. TaxType.default_rate değişse bile geçmiş kayıtlar korunur.';
COMMENT ON COLUMN finance.tax_calculation_log.calculation_source IS
    'Hesaplamayı yapan motor/versiyon etiketi (örn: TAX_ENGINE_V1, MANUAL_ENTRY).';

-- -----------------------------------------------------------------------------
-- 3. İndeksler
-- -----------------------------------------------------------------------------

-- Birincil filtreleme indeksi: işlem tipi + referans ID
-- Prompt gereksinimi: bu iki alanın bileşik indeksi
CREATE INDEX IF NOT EXISTS idx_tax_log_tx_type_ref
    ON finance.tax_calculation_log (transaction_type, transaction_reference_id);

-- Dönemsel raporlama için tarih indeksi
CREATE INDEX IF NOT EXISTS idx_tax_log_calculation_date
    ON finance.tax_calculation_log (calculation_date DESC);

-- Muafiyet raporlaması için parsiyel indeks (sadece muaf kayıtlar)
CREATE INDEX IF NOT EXISTS idx_tax_log_is_exempt
    ON finance.tax_calculation_log (is_exempt)
    WHERE is_exempt = TRUE;

-- Vergi tipi bazında raporlama için
CREATE INDEX IF NOT EXISTS idx_tax_log_tax_type_id
    ON finance.tax_calculation_log (tax_type_id);

-- Motor denetimi için (opsiyonel, düşük kardinalite)
CREATE INDEX IF NOT EXISTS idx_tax_log_calculation_source
    ON finance.tax_calculation_log (calculation_source);

-- -----------------------------------------------------------------------------
-- 4. Başlangıç Seed Verisi — Temel Vergi Tipleri
-- -----------------------------------------------------------------------------
INSERT INTO finance.tax_type (id, code, description, default_rate, active)
VALUES
    (gen_random_uuid(), 'KDV_1',   'Katma Değer Vergisi %1',   1.00,  TRUE),
    (gen_random_uuid(), 'KDV_8',   'Katma Değer Vergisi %8',   8.00,  TRUE),
    (gen_random_uuid(), 'KDV_18',  'Katma Değer Vergisi %18',  18.00, TRUE),
    (gen_random_uuid(), 'KDV_20',  'Katma Değer Vergisi %20',  20.00, TRUE),
    (gen_random_uuid(), 'OTV_25',  'Özel Tüketim Vergisi %25', 25.00, TRUE),
    (gen_random_uuid(), 'OTV_45',  'Özel Tüketim Vergisi %45', 45.00, TRUE),
    (gen_random_uuid(), 'STOPAJ_10', 'Stopaj Vergisi %10',     10.00, TRUE);
