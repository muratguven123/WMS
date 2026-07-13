-- =============================================================================
-- V12.1 — Yerel ve Uluslararası Adres Formatları
-- WMS Localization Service — PostgreSQL 15+
-- UTF-8 zorunludur; cluster seviyesinde CREATE DATABASE ile garanti altına alınır.
-- =============================================================================

-- Şema oluştur (yoksa)
CREATE SCHEMA IF NOT EXISTS localization;

-- =============================================================================
-- 1. address_template_field
--    Ülkeden bağımsız alan tanımları (district, neighborhood, door_no …)
-- =============================================================================
CREATE TABLE IF NOT EXISTS localization.address_template_field (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    field_key       VARCHAR(100) NOT NULL,          -- örn: "district"
    field_label_key VARCHAR(255) NOT NULL,           -- örn: "fields.district"

    CONSTRAINT pk_address_template_field PRIMARY KEY (id),
    CONSTRAINT uq_address_template_field_key UNIQUE (field_key)
);

COMMENT ON TABLE  localization.address_template_field              IS 'Ülkeden bağımsız adres alan şablonları';
COMMENT ON COLUMN localization.address_template_field.field_key    IS 'Benzersiz alan anahtarı — JSONB Map key ile eşleşir';
COMMENT ON COLUMN localization.address_template_field.field_label_key IS 'i18n mesaj anahtarı';

-- =============================================================================
-- 2. country_address_template
--    Ülke × alan eşleşmesi; zorunluluk, sıra ve validasyon kuralları
-- =============================================================================
CREATE TABLE IF NOT EXISTS localization.country_address_template (
    id                        UUID         NOT NULL DEFAULT gen_random_uuid(),
    country_id                UUID         NOT NULL,
    address_template_field_id UUID         NOT NULL,
    is_mandatory              BOOLEAN      NOT NULL DEFAULT FALSE,
    sequence                  INT          NOT NULL DEFAULT 0,
    validation_regex          VARCHAR(500),
    error_message_key         VARCHAR(255),

    CONSTRAINT pk_country_address_template  PRIMARY KEY (id),
    CONSTRAINT fk_cat_template_field
        FOREIGN KEY (address_template_field_id)
        REFERENCES localization.address_template_field (id)
        ON DELETE RESTRICT,

    -- Aynı ülkede aynı alan bir kez tanımlanabilir
    CONSTRAINT uq_cat_country_field UNIQUE (country_id, address_template_field_id)
);

COMMENT ON TABLE  localization.country_address_template                 IS 'Ülkeye özgü adres alan şablonları';
COMMENT ON COLUMN localization.country_address_template.country_id      IS 'wms-core-service Country UUID (cross-service ref)';
COMMENT ON COLUMN localization.country_address_template.is_mandatory    IS 'Bu alan söz konusu ülke için zorunlu mu?';
COMMENT ON COLUMN localization.country_address_template.sequence        IS 'Form gösterim sırası';
COMMENT ON COLUMN localization.country_address_template.validation_regex IS 'Opsiyonel validasyon regex';

-- Ülke bazlı şablon sorguları için bileşik index
CREATE INDEX IF NOT EXISTS idx_cat_country_id
    ON localization.country_address_template (country_id);

CREATE INDEX IF NOT EXISTS idx_cat_country_sequence
    ON localization.country_address_template (country_id, sequence);

-- =============================================================================
-- 3. address
--    Dinamik adres kayıtları — ülkeye özgü alanlar JSONB içinde saklanır
-- =============================================================================
CREATE TABLE IF NOT EXISTS localization.address (
    id                UUID            NOT NULL DEFAULT gen_random_uuid(),
    country_id        UUID            NOT NULL,
    city              VARCHAR(150),
    state             VARCHAR(150),
    zip_code          VARCHAR(20),
    -- Ülkeye özgü dinamik alanlar (key = field_key, value = kullanıcı girişi)
    address_details   JSONB,
    -- İnsan tarafından okunabilir tam adres satırı
    formatted_address TEXT,
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_address PRIMARY KEY (id)
);

COMMENT ON TABLE  localization.address                  IS 'Dinamik adres kayıtları';
COMMENT ON COLUMN localization.address.country_id       IS 'wms-core-service Country UUID (cross-service ref)';
COMMENT ON COLUMN localization.address.address_details  IS 'Ülkeye özgü dinamik alanlar (JSONB)';
COMMENT ON COLUMN localization.address.formatted_address IS 'İnsan okunabilir biçimlendirilmiş adres satırı';

-- -----------------------------------------------------------------------------
-- B-tree indexes — sabit sütunlarda nokta/aralık sorguları
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_address_country_id
    ON localization.address (country_id);

CREATE INDEX IF NOT EXISTS idx_address_city
    ON localization.address (city);

CREATE INDEX IF NOT EXISTS idx_address_state
    ON localization.address (state);

CREATE INDEX IF NOT EXISTS idx_address_zip_code
    ON localization.address (zip_code);

-- -----------------------------------------------------------------------------
-- GIN index — JSONB içinde hızlı @> (containment) sorguları
-- Desteklenen operatörler: @>, ?, ?|, ?&
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_address_details_gin
    ON localization.address USING gin (address_details);

-- =============================================================================
-- Seed: Yaygın AddressTemplateField kayıtları
-- =============================================================================
INSERT INTO localization.address_template_field (id, field_key, field_label_key)
VALUES
    (gen_random_uuid(), 'district',      'fields.district'),
    (gen_random_uuid(), 'neighborhood',  'fields.neighborhood'),
    (gen_random_uuid(), 'street',        'fields.street'),
    (gen_random_uuid(), 'door_no',       'fields.door_no'),
    (gen_random_uuid(), 'apartment_no',  'fields.apartment_no'),
    (gen_random_uuid(), 'floor',         'fields.floor'),
    (gen_random_uuid(), 'building_name', 'fields.building_name'),
    (gen_random_uuid(), 'po_box',        'fields.po_box'),
    (gen_random_uuid(), 'province',      'fields.province'),
    (gen_random_uuid(), 'prefecture',    'fields.prefecture')
ON CONFLICT (field_key) DO NOTHING;
