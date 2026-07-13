-- =====================================================================
-- V10: Dynamic UI Engine Schema
-- İş İsteri 5 — Lokasyon ve Yetki Bazlı Dinamik Ekran Alan Yönetimi
-- Flyway migration
-- =====================================================================

-- ── Enum Types ────────────────────────────────────────────────────────

CREATE TYPE field_behavior AS ENUM (
    'MANDATORY',
    'HIDDEN',
    'READ_ONLY',
    'OPTIONAL'
);

CREATE TYPE field_data_type AS ENUM (
    'STRING',
    'NUMBER',
    'DATE',
    'SELECT'
);

-- ── screens ───────────────────────────────────────────────────────────
-- Sistemde tanımlı form/ekranların kataloğu.
-- code değeri frontend ile kontrattır; değiştirilmemelidir.

CREATE TABLE screens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(100) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT uk_screen_code UNIQUE (code)
);

COMMENT ON TABLE  screens      IS 'Sistemde tanımlı ekran/form kataloğu';
COMMENT ON COLUMN screens.code IS 'Frontend ile kontrakt — örn: MAT_CARD_FORM, REC_CONTROL_FORM';

-- ── screen_fields ─────────────────────────────────────────────────────
-- Her ekrana ait form alanlarının tanımı.

CREATE TABLE screen_fields (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    screen_id        UUID            NOT NULL,
    field_key        VARCHAR(100)    NOT NULL,
    default_behavior field_behavior  NOT NULL,
    data_type        field_data_type NOT NULL,
    is_active        BOOLEAN         NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,

    CONSTRAINT fk_screen_field_screen
        FOREIGN KEY (screen_id) REFERENCES screens (id) ON DELETE RESTRICT,

    CONSTRAINT uk_screen_field_key
        UNIQUE (screen_id, field_key)
);

COMMENT ON TABLE  screen_fields              IS 'Ekranlara ait form alan tanımları';
COMMENT ON COLUMN screen_fields.field_key    IS 'Alan kimliği — aynı zamanda çeviri anahtarı: field.<fieldKey>.label';
COMMENT ON COLUMN screen_fields.default_behavior IS 'Hiçbir kural eşleşmediğinde uygulanacak davranış';

CREATE INDEX idx_screen_fields_screen ON screen_fields (screen_id);

-- ── field_behavior_rules ──────────────────────────────────────────────
-- Alanlara bağlı, bağlam bazlı (lokasyon/rol/şirket/ülke) davranış kuralları.
--
-- Kural eşleşme mantığı:
--   nullable bağlam alanı = "herkes/her durum için geçerli"
--   priority büyük → küçük sıralanır; en yüksek öncelikli kural kazanır.
--
-- Varsayılan öncelik hiyerarşisi:
--   location_id → 50 | role_id → 40 | company_id → 30 | country_id → 20 | global → 10

CREATE TABLE field_behavior_rules (
    id                           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    screen_field_id              UUID           NOT NULL,

    -- Kural önceliği (büyük değer kazanır)
    priority                     INTEGER        NOT NULL,

    -- Bağlam filtreleri (NULL = "hepsi için geçerli")
    company_id                   UUID,
    country_id                   UUID,
    location_id                  UUID,
    role_id                      UUID,
    operation_type               VARCHAR(50),

    -- Kural çıktısı
    behavior                     field_behavior NOT NULL,
    default_value                VARCHAR(500),
    validation_regex             VARCHAR(500),
    validation_error_message_key VARCHAR(200),

    updated_at                   TIMESTAMPTZ,

    CONSTRAINT fk_fbr_screen_field
        FOREIGN KEY (screen_field_id) REFERENCES screen_fields (id) ON DELETE CASCADE
);

COMMENT ON TABLE  field_behavior_rules                       IS 'Ekran alanlarına ait bağlamsal davranış kuralları';
COMMENT ON COLUMN field_behavior_rules.priority              IS 'Çakışan kurallarda büyük değer kazanır. Lokasyon=50, Rol=40, Şirket=30, Ülke=20, Global=10';
COMMENT ON COLUMN field_behavior_rules.company_id            IS 'NULL → tüm şirketler için geçerli';
COMMENT ON COLUMN field_behavior_rules.country_id            IS 'NULL → tüm ülkeler için geçerli';
COMMENT ON COLUMN field_behavior_rules.location_id           IS 'NULL → tüm lokasyonlar için geçerli';
COMMENT ON COLUMN field_behavior_rules.role_id               IS 'NULL → tüm roller için geçerli';
COMMENT ON COLUMN field_behavior_rules.operation_type        IS 'NULL → tüm operasyon tipleri; örn: CREATE, EDIT, VIEW';
COMMENT ON COLUMN field_behavior_rules.validation_regex      IS 'Sunucu tarafı validasyon deseni. NULL → regex kontrolü yok';
COMMENT ON COLUMN field_behavior_rules.validation_error_message_key IS 'Regex başarısızlığında kullanılacak çeviri anahtarı';

CREATE INDEX idx_fbr_screen_field ON field_behavior_rules (screen_field_id);
CREATE INDEX idx_fbr_location     ON field_behavior_rules (location_id);
CREATE INDEX idx_fbr_role         ON field_behavior_rules (role_id);
CREATE INDEX idx_fbr_company      ON field_behavior_rules (company_id);
CREATE INDEX idx_fbr_country      ON field_behavior_rules (country_id);

-- ── Demo Seed — Geliştirme Ortamı ─────────────────────────────────────

-- REC_CONTROL_FORM ekranı
INSERT INTO screens (id, code, name) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'REC_CONTROL_FORM', 'Mal Kabul Kontrol Formu'),
    ('a1000000-0000-0000-0000-000000000002', 'MAT_CARD_FORM',    'Malzeme Kartı Formu');

-- REC_CONTROL_FORM alanları
INSERT INTO screen_fields (id, screen_id, field_key, default_behavior, data_type) VALUES
    ('b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'tax_number', 'OPTIONAL',  'STRING'),
    ('b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'district',   'OPTIONAL',  'STRING'),
    ('b1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'zip_code',   'OPTIONAL',  'STRING'),
    ('b1000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000001', 'batch_no',   'HIDDEN',    'STRING');

-- Örnek kurallar:
--   1. Tüm ülke TR için tax_number → MANDATORY, 10 haneli regex
--   2. Belirli lokasyon için district → HIDDEN (ülke kuralını ezer, priority=50)
INSERT INTO field_behavior_rules
    (id, screen_field_id, priority, country_id, behavior, validation_regex, validation_error_message_key)
VALUES
    ('c1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000001',
     20,
     null,          -- tüm ülkeler (demo; gerçek senaryoda country UUID)
     'MANDATORY',
     '^\d{10}$',
     'validation.tax_number.invalid');

INSERT INTO field_behavior_rules
    (id, screen_field_id, priority, location_id, behavior)
VALUES
    ('c1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000002',
     50,
     null,          -- tüm lokasyonlar (demo; gerçek senaryoda location UUID)
     'HIDDEN');
