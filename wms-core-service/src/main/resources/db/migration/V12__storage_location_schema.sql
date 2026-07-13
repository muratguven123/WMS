-- =====================================================================
-- V12: Faz 5 — Depo Fiziksel Yerleşim & Adres Yönetimi
--
-- 1. zones tablosuna code ve description sütunları eklenir.
-- 2. storage_location_status ENUM tipi oluşturulur.
-- 3. storage_locations tablosu oluşturulur.
-- 4. İndeksler tanımlanır.
--
-- Flyway migration — wms-core-service
-- =====================================================================

-- -----------------------------------------------------------------------
-- BÖLÜM 1: zones tablosuna yeni sütunlar
-- -----------------------------------------------------------------------

-- Faz 5 ile birlikte Zone'ların makine okunabilir bir kodu (code) ve
-- açıklama (description) alanı alması gerekmektedir.

ALTER TABLE zones
    ADD COLUMN IF NOT EXISTS code        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- Mevcut kayıtların code alanını geçici olarak name değeriyle doldur
-- (production ortamı için migration script'e veri düzeltme adımı eklenmeli)
UPDATE zones
SET code = UPPER(REPLACE(name, ' ', '_'))
WHERE code IS NULL;

-- Sütunu NOT NULL kısıtına çek (veri doldurulduktan sonra)
ALTER TABLE zones
    ALTER COLUMN code SET NOT NULL;

-- Aynı depo içinde code benzersiz olmalı
ALTER TABLE zones
    DROP CONSTRAINT IF EXISTS uk_zone_location_code;

ALTER TABLE zones
    ADD CONSTRAINT uk_zone_location_code UNIQUE (location_id, code);

-- -----------------------------------------------------------------------
-- BÖLÜM 2: Yeni ENUM tipi
-- -----------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'storage_location_status') THEN
        CREATE TYPE storage_location_status AS ENUM (
            'ACTIVE',    -- Göz kullanılabilir; yeni stok kabul eder
            'BLOCKED',   -- Manuel olarak kapatılmış; stok girişi yasak
            'FULL'       -- Kapasite doldu; sistem tarafından otomatik atanır
        );
    END IF;
END
$$;

-- -----------------------------------------------------------------------
-- BÖLÜM 3: storage_locations tablosu
-- -----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS storage_locations (
    -- ---- Kimlik ----
    id              UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ---- İlişki ----
    zone_id         UUID                        NOT NULL,

    -- ---- Adres Hiyerarşisi ----
    -- Koridor → Bölüm → Kat → Göz
    aisle           VARCHAR(10)                 NOT NULL,   -- Koridor: A, B, C
    bay             VARCHAR(10)                 NOT NULL,   -- Bölüm : 01, 02
    shelf           VARCHAR(10)                 NOT NULL,   -- Kat   : 01, 02, 03
    bin             VARCHAR(10)                 NOT NULL,   -- Göz   : 01, 02

    -- Otomatik üretilen adres kodu — @PrePersist ile doldurulur
    -- Format: [aisle]-[bay]-[shelf]-[bin] → Örn: A-01-03-01
    address_code    VARCHAR(50)                 NOT NULL,

    -- ---- Kapasite ----
    max_volume      NUMERIC(10, 4)              NOT NULL,   -- Maksimum hacim (m³)
    max_weight      NUMERIC(10, 4)              NOT NULL,   -- Maksimum ağırlık (kg)
    current_volume  NUMERIC(10, 4)              NOT NULL DEFAULT 0, -- Mevcut hacim yükü
    current_weight  NUMERIC(10, 4)              NOT NULL DEFAULT 0, -- Mevcut ağırlık yükü

    -- ---- Durum ----
    status          storage_location_status     NOT NULL DEFAULT 'ACTIVE',

    -- ---- Audit ----
    is_active       BOOLEAN                     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ                 NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,

    -- ---- Kısıtlar ----

    -- FK: zone_id → zones.id
    CONSTRAINT fk_sl_zone
        FOREIGN KEY (zone_id) REFERENCES zones (id),

    -- Adres kodu globally unique olmalı
    CONSTRAINT uk_sl_address_code
        UNIQUE (address_code),

    -- Aynı zone içinde aynı adres kombinasyonu olamaz
    CONSTRAINT uk_sl_zone_aisle_bay_shelf_bin
        UNIQUE (zone_id, aisle, bay, shelf, bin),

    -- Kapasite değerleri negatif olamaz
    CONSTRAINT chk_sl_max_volume_positive
        CHECK (max_volume > 0),
    CONSTRAINT chk_sl_max_weight_positive
        CHECK (max_weight > 0),
    CONSTRAINT chk_sl_current_volume_non_negative
        CHECK (current_volume >= 0),
    CONSTRAINT chk_sl_current_weight_non_negative
        CHECK (current_weight >= 0),

    -- Mevcut yük maksimumu aşamaz
    CONSTRAINT chk_sl_volume_within_max
        CHECK (current_volume <= max_volume),
    CONSTRAINT chk_sl_weight_within_max
        CHECK (current_weight <= max_weight)
);

COMMENT ON TABLE  storage_locations                IS 'Depo içindeki fiziksel raf gözlerini (bin) temsil eder. Adres hiyerarşisi: aisle → bay → shelf → bin.';
COMMENT ON COLUMN storage_locations.address_code   IS 'Otomatik üretilen benzersiz adres kodu. Format: [aisle]-[bay]-[shelf]-[bin]. Örn: A-01-03-01';
COMMENT ON COLUMN storage_locations.max_volume     IS 'Gözün maksimum hacim kapasitesi (m³).';
COMMENT ON COLUMN storage_locations.max_weight     IS 'Gözün maksimum ağırlık kapasitesi (kg).';
COMMENT ON COLUMN storage_locations.current_volume IS 'Gözde mevcut stoğun toplam hacmi. LocationCapacityService tarafından güncellenir.';
COMMENT ON COLUMN storage_locations.current_weight IS 'Gözde mevcut stoğun toplam ağırlığı. LocationCapacityService tarafından güncellenir.';

-- -----------------------------------------------------------------------
-- BÖLÜM 4: İndeksler
-- -----------------------------------------------------------------------

-- Zone'a göre arama (en yaygın sorgu deseni)
CREATE INDEX IF NOT EXISTS idx_sl_zone_id
    ON storage_locations (zone_id);

-- Durum bazlı filtreleme (ACTIVE gözleri bul)
CREATE INDEX IF NOT EXISTS idx_sl_status
    ON storage_locations (status)
    WHERE is_active = TRUE;

-- Koridor bazlı arama (depo şefi filtreleme)
CREATE INDEX IF NOT EXISTS idx_sl_aisle
    ON storage_locations (aisle)
    WHERE is_active = TRUE;

-- Kapasite doluluk oranı hesaplama için partial index
-- (Directed Putaway sorgularını hızlandırır)
CREATE INDEX IF NOT EXISTS idx_sl_zone_active_capacity
    ON storage_locations (zone_id, current_volume, current_weight)
    WHERE status = 'ACTIVE' AND is_active = TRUE;
