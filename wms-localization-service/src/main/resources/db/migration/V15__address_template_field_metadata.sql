-- =============================================================================
-- V15 — address_template_field alan tipi / master-data kaynağı / bağımlılık metadata'sı
--
-- Amaç: UI'ın hangi şablon alanının serbest metin, hangisinin core-service
-- master-data cascade'ine bağlı bir seçim kutusu olduğunu, ve seçim kutusuysa
-- hangi üst alana bağlı olduğunu backend'den okuyabilmesi. Bu sayede UI'da
-- field-key ismine göre hardcode switch yazılmasına gerek kalmaz.
--
-- field_type          : 'TEXT' | 'MASTER_SELECT' | 'FIXED'
--                       FIXED: alan zaten sabit UI elemanları (country/state/city
--                       cascade'i, tekil zip input'u) tarafından render ediliyor;
--                       bu tip sadece "city"/"state"/"zip_code" için template'e
--                       mandatory/regex kuralı taşımak amacıyla kullanılır,
--                       DynamicAddressField bu alanları ayrıca render etmez.
-- master_data_source  : 'NONE' | 'DISTRICT' | 'NEIGHBORHOOD'
--                       (core-service cascade endpoint'lerinden hangisinin
--                        çağrılacağını belirtir; sadece MASTER_SELECT için anlamlı)
-- parent_field_key     : bu alanın seçili değerinin hangi üst alana bağlı
--                        olduğunu belirtir. Sabit (template dışı) alanlara
--                        referans için özel anahtarlar kullanılır:
--                        '__city__'  → Address.city / core cities.id
--                        '__state__' → Address.state / core state_provinces.id
--                        Template içi bir alana bağımlılık için o alanın
--                        field_key'i kullanılır (örn. neighborhood → 'district').
--
-- Additive migration; mevcut satırlar DEFAULT değerlerle güvenle dolar.
-- =============================================================================

ALTER TABLE localization.address_template_field
    ADD COLUMN IF NOT EXISTS field_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS master_data_source VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS parent_field_key VARCHAR(100);

ALTER TABLE localization.address_template_field
    DROP CONSTRAINT IF EXISTS chk_atf_field_type;
ALTER TABLE localization.address_template_field
    ADD CONSTRAINT chk_atf_field_type
        CHECK (field_type IN ('TEXT', 'MASTER_SELECT', 'FIXED'));

ALTER TABLE localization.address_template_field
    DROP CONSTRAINT IF EXISTS chk_atf_master_data_source;
ALTER TABLE localization.address_template_field
    ADD CONSTRAINT chk_atf_master_data_source
        CHECK (master_data_source IN ('NONE', 'DISTRICT', 'NEIGHBORHOOD'));

COMMENT ON COLUMN localization.address_template_field.field_type
    IS 'Alanın giriş tipi: TEXT (serbest metin) veya MASTER_SELECT (core-service master-data cascade seçimi)';
COMMENT ON COLUMN localization.address_template_field.master_data_source
    IS 'MASTER_SELECT ise hangi core-service cascade kaynağının kullanılacağı (DISTRICT/NEIGHBORHOOD)';
COMMENT ON COLUMN localization.address_template_field.parent_field_key
    IS 'Bu alanın bağımlı olduğu üst alan; __city__/__state__ sabit alanlara, diğerleri başka bir field_key''e işaret eder';

-- ---------------------------------------------------------------------------
-- Backfill — mevcut iki cascade alanı
-- ---------------------------------------------------------------------------
UPDATE localization.address_template_field
SET field_type = 'MASTER_SELECT',
    master_data_source = 'DISTRICT',
    parent_field_key = '__city__'
WHERE field_key = 'district';

UPDATE localization.address_template_field
SET field_type = 'MASTER_SELECT',
    master_data_source = 'NEIGHBORHOOD',
    parent_field_key = 'district'
WHERE field_key = 'neighborhood';

-- zip_code de FIXED sayılır: Address.zipCode sabit sütunundan render edilir
-- (UI'da her zaman gösterilen tek bir posta kodu input'u vardır); template
-- satırı sadece mandatory/regex kuralını taşımak için kullanılır — tıpkı
-- city/state gibi.
UPDATE localization.address_template_field
SET field_type = 'FIXED'
WHERE field_key = 'zip_code';

-- Diğer tüm mevcut alanlar (street, door_no, apartment_no, floor,
-- building_name, po_box, province, prefecture) DEFAULT 'TEXT' / 'NONE' / NULL
-- olarak kalır — ek işlem gerekmez.

-- ---------------------------------------------------------------------------
-- Yeni field_key'ler — 15 ülke matrisinde ihtiyaç duyulan ek serbest metin alanları
-- (bkz. WMS_Dinamik_Adres_Plani_v3.md §2.1)
-- ---------------------------------------------------------------------------
INSERT INTO localization.address_template_field (field_key, field_label_key, field_type, master_data_source)
VALUES
    ('house_no',      'fields.house_no',      'TEXT', 'NONE'),  -- ev/bina no (RU, NL, PL, DE, FR, PT vb.)
    ('korpus',        'fields.korpus',         'TEXT', 'NONE'),  -- Rusya: blok/giriş no
    ('additional_no',  'fields.additional_no', 'TEXT', 'NONE'),  -- Suudi Arabistan: ek 4 haneli posta kodu
    ('chome_banchi',  'fields.chome_banchi',   'TEXT', 'NONE'),  -- Japonya: chōme-banchi-gō
    ('road_name',     'fields.road_name',      'TEXT', 'NONE'),  -- Güney Kore: yol adı tabanlı adres
    ('locality',      'fields.locality',       'TEXT', 'NONE'),  -- Hindistan: mahalle/bölge (serbest metin)
    ('landmark',      'fields.landmark',       'TEXT', 'NONE')   -- Hindistan: referans nokta
ON CONFLICT (field_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- "city" / "state" — FIXED tip: bu alanlar zaten sabit country→state→city
-- cascade'i tarafından render ediliyor (Address.city / Address.state sütunları).
-- Buradaki tek amaç, country_address_template üzerinden bazı ülkelerde
-- "state zorunlu mu" kuralını tanımlayabilmek — AddressValidationService
-- zaten "city"/"state" fieldKey'lerini Address'in sabit sütunlarından okuyor
-- (bkz. AddressValidationService.extractFieldValue).
-- ---------------------------------------------------------------------------
INSERT INTO localization.address_template_field (field_key, field_label_key, field_type, master_data_source)
VALUES
    ('city',  'fields.city',  'FIXED', 'NONE'),
    ('state', 'fields.state', 'FIXED', 'NONE')
ON CONFLICT (field_key) DO NOTHING;
