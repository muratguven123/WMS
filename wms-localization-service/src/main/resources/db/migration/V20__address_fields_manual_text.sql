-- =============================================================================
-- V20 — Cascade alanlarını serbest metin girişine çevir
--
-- Ülke seçimi (core /api/address/countries) dışındaki tüm adres alanları
-- (eyalet, il, ilçe, mahalle, cadde/sokak vb.) elle girilir.
-- =============================================================================

UPDATE localization.address_template_field
SET field_type = 'TEXT',
    master_data_source = 'NONE',
    parent_field_key = NULL
WHERE field_key IN ('state', 'city', 'district', 'neighborhood');

COMMENT ON COLUMN localization.address_template_field.field_type
    IS 'Alanın giriş tipi: TEXT (serbest metin) veya FIXED (zip_code sabit input)';

-- ---------------------------------------------------------------------------
-- TR şablonu: city ve street ekle; mevcut sıralamayı güncelle
-- (önceden city yalnızca cascade parent olarak UI'da sentetik ekleniyordu)
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE tmp_core_countries AS
SELECT * FROM dblink(
    'dbname=wms_core_db user=postgres password=postgres',
    'SELECT id, iso_code FROM countries'
) AS t(id BIGINT, iso_code VARCHAR(3));

UPDATE localization.country_address_template cat
SET sequence = v.new_seq
FROM tmp_core_countries cc,
     (VALUES
        ('TR', 'district', 2),
        ('TR', 'neighborhood', 3),
        ('TR', 'zip_code', 5)
     ) AS v(iso_code, field_key, new_seq)
JOIN localization.address_template_field f ON f.field_key = v.field_key
WHERE cat.country_id = cc.id
  AND cc.iso_code = v.iso_code
  AND cat.address_template_field_id = f.id;

INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT cc.id, f.id, v.is_mandatory, v.sequence,
       NULLIF(v.validation_regex, ''),
       v.error_message_key
FROM (VALUES
    ('TR', 'city',   TRUE,  1, '', 'validation.city.required'),
    ('TR', 'street', TRUE,  4, '', 'validation.street.required')
) AS v(iso_code, field_key, is_mandatory, sequence, validation_regex, error_message_key)
JOIN tmp_core_countries cc ON cc.iso_code = v.iso_code
JOIN localization.address_template_field f ON f.field_key = v.field_key
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

DROP TABLE tmp_core_countries;
