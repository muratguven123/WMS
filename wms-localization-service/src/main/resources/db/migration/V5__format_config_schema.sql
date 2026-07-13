-- =============================================================================
-- Flyway Migration: V5__format_config_schema.sql
-- Ülke ve depo bazlı tarih/saat/sayısal format yapılandırma tabloları
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. country_format_config
--    Her ülke için varsayılan biçimlendirme kurallarını saklar.
--    Bir deponun kendi kaydı yoksa bu tablo devreye girer.
-- ---------------------------------------------------------------------------
CREATE TABLE country_format_config
(
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    country_id         UUID        NOT NULL,
    date_format        VARCHAR(20) NOT NULL,
    time_format        VARCHAR(20) NOT NULL,
    decimal_separator  VARCHAR(1)  NOT NULL,
    thousand_separator VARCHAR(1)  NOT NULL,
    updated_at         TIMESTAMPTZ,

    CONSTRAINT pk_country_format_config    PRIMARY KEY (id),
    CONSTRAINT uq_cfc_country_id           UNIQUE      (country_id),
    CONSTRAINT chk_cfc_decimal_separator   CHECK       (decimal_separator   IN ('.', ',')),
    CONSTRAINT chk_cfc_thousand_separator  CHECK       (thousand_separator  IN ('.', ','))
);

CREATE INDEX idx_cfc_country_id ON country_format_config (country_id);

COMMENT ON TABLE  country_format_config                    IS 'Ülke bazlı varsayılan tarih/saat/sayısal biçimlendirme kuralları';
COMMENT ON COLUMN country_format_config.country_id         IS 'wms-core-service Country entity UUID — her ülke için tek kayıt';
COMMENT ON COLUMN country_format_config.date_format        IS 'Java DateTimeFormatter deseni: dd.MM.yyyy, MM/dd/yyyy, vb.';
COMMENT ON COLUMN country_format_config.time_format        IS 'Java DateTimeFormatter deseni: HH:mm, hh:mm a, vb.';
COMMENT ON COLUMN country_format_config.decimal_separator  IS 'Ondalık ayraç: "," (Avrupa) veya "." (ABD)';
COMMENT ON COLUMN country_format_config.thousand_separator IS 'Binlik ayraç: "." (Avrupa) veya "," (ABD)';

-- ---------------------------------------------------------------------------
-- 2. location_format_override
--    Belirli bir deponun ülke varsayılanını geçersiz kıldığı durumlar.
--    Kayıt yoksa country_format_config'e fall-back yapılır.
--    Alan null ise o alan için ülke varsayılanı geçerlidir.
-- ---------------------------------------------------------------------------
CREATE TABLE location_format_override
(
    id                 UUID       NOT NULL DEFAULT gen_random_uuid(),
    location_id        UUID       NOT NULL,
    date_format        VARCHAR(20),
    time_format        VARCHAR(20),
    decimal_separator  VARCHAR(1),
    thousand_separator VARCHAR(1),
    updated_at         TIMESTAMPTZ,

    CONSTRAINT pk_location_format_override   PRIMARY KEY (id),
    CONSTRAINT uq_lfo_location_id            UNIQUE      (location_id),
    CONSTRAINT chk_lfo_decimal_separator     CHECK       (decimal_separator  IS NULL OR decimal_separator  IN ('.', ',')),
    CONSTRAINT chk_lfo_thousand_separator    CHECK       (thousand_separator IS NULL OR thousand_separator IN ('.', ','))
);

CREATE INDEX idx_lfo_location_id ON location_format_override (location_id);

COMMENT ON TABLE  location_format_override                    IS 'Depo bazlı format geçersiz kılma; yoksa country_format_config devreye girer';
COMMENT ON COLUMN location_format_override.location_id        IS 'wms-core-service Location entity UUID — her depo için en fazla bir kayıt';
COMMENT ON COLUMN location_format_override.date_format        IS 'NULL ise ülke varsayılanı kullanılır';
COMMENT ON COLUMN location_format_override.time_format        IS 'NULL ise ülke varsayılanı kullanılır';
COMMENT ON COLUMN location_format_override.decimal_separator  IS 'NULL ise ülke varsayılanı kullanılır';
COMMENT ON COLUMN location_format_override.thousand_separator IS 'NULL ise ülke varsayılanı kullanılır';
