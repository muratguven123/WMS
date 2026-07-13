-- =============================================================================
-- Flyway Migration: V2__i18n_schema.sql
-- Çok Dilli Arayüz ve Raporlama Altyapısı
-- Oluşturma Tarihi: 2026-07-03
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. language
--    Sistemde tanımlı dilleri tutar.
--    code : ISO 639-1 (tr, en, de …)
-- -----------------------------------------------------------------------------
CREATE TABLE language
(
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    code       VARCHAR(10) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    is_default BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ,

    CONSTRAINT pk_language PRIMARY KEY (id),
    CONSTRAINT uq_language_code UNIQUE (code)
);

CREATE INDEX idx_language_code      ON language (code);
CREATE INDEX idx_language_is_active ON language (is_active);

COMMENT ON TABLE  language            IS 'Sistemde desteklenen diller';
COMMENT ON COLUMN language.code       IS 'ISO 639-1 dil kodu (tr, en, de)';
COMMENT ON COLUMN language.is_default IS 'Yalnızca bir satır TRUE olabilir';

-- -----------------------------------------------------------------------------
-- 2. translation_key
--    Çevrilen metin anahtarlarını tutar.
--    module : UI | REPORT | EMAIL | SYSTEM
-- -----------------------------------------------------------------------------
CREATE TABLE translation_key
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    key_code    VARCHAR(255) NOT NULL,
    module      VARCHAR(50)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_translation_key    PRIMARY KEY (id),
    CONSTRAINT uq_translation_key_code UNIQUE (key_code),
    CONSTRAINT chk_translation_key_module CHECK (module IN ('UI', 'REPORT', 'EMAIL', 'SYSTEM'))
);

CREATE INDEX idx_translation_key_code   ON translation_key (key_code);
CREATE INDEX idx_translation_key_module ON translation_key (module);

COMMENT ON TABLE  translation_key             IS 'Uygulama geneli çeviri anahtar tanımları';
COMMENT ON COLUMN translation_key.key_code    IS 'Nokta-notasyonlu anahtar: common.buttons.save';
COMMENT ON COLUMN translation_key.module      IS 'UI | REPORT | EMAIL | SYSTEM';

-- -----------------------------------------------------------------------------
-- 3. translation_value
--    Anahtar + Dil kombinasyonuna ait çeviri metnini tutar.
--    Benzersizlik: (language_id, translation_key_id) çifti tekrar edemez.
-- -----------------------------------------------------------------------------
CREATE TABLE translation_value
(
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    language_id         UUID        NOT NULL,
    translation_key_id  UUID        NOT NULL,
    value               TEXT        NOT NULL,
    updated_at          TIMESTAMPTZ,

    CONSTRAINT pk_translation_value            PRIMARY KEY (id),
    CONSTRAINT uq_translation_value_lang_key   UNIQUE (language_id, translation_key_id),
    CONSTRAINT fk_translation_value_language   FOREIGN KEY (language_id)
        REFERENCES language (id) ON DELETE RESTRICT,
    CONSTRAINT fk_translation_value_key        FOREIGN KEY (translation_key_id)
        REFERENCES translation_key (id) ON DELETE CASCADE
);

CREATE INDEX idx_translation_value_language_id        ON translation_value (language_id);
CREATE INDEX idx_translation_value_translation_key_id ON translation_value (translation_key_id);

-- Arama performansı: dil + anahtar birlikte sık sorgulanır
CREATE INDEX idx_translation_value_lang_key ON translation_value (language_id, translation_key_id);

COMMENT ON TABLE  translation_value                   IS 'Anahtar-Dil çifti için çeviri metni';
COMMENT ON COLUMN translation_value.value             IS 'Çevrilmiş metin; uzun içerik için TEXT tipi';

-- -----------------------------------------------------------------------------
-- 4. Seed: Varsayılan diller
--    Minimum canlı veri — uygulama başlangıcı için.
-- -----------------------------------------------------------------------------
INSERT INTO language (code, name, is_default, is_active)
VALUES
    ('tr', 'Türkçe', TRUE,  TRUE),
    ('en', 'English', FALSE, TRUE);
