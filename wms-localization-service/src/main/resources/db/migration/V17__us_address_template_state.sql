-- =============================================================================
-- V17 — US adres şablonuna zorunlu state (FIXED) kuralı
--
-- V12_2 yalnızca street + zip_code seed eder; v3 matrisi US için state
-- zorunlu tanımlar. Core V9'da California state verisi zaten mevcut.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE tmp_core_countries AS
SELECT * FROM dblink(
    'dbname=wms_core_db user=postgres password=postgres',
    'SELECT id, iso_code FROM countries'
) AS t(id BIGINT, iso_code VARCHAR(3));

INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT cc.id, f.id, v.is_mandatory, v.sequence,
       NULLIF(v.validation_regex, ''),
       v.error_message_key
FROM (VALUES
    ('US', 'state', TRUE, 0, '', 'validation.state.required')
) AS v(iso_code, field_key, is_mandatory, sequence, validation_regex, error_message_key)
JOIN tmp_core_countries cc ON cc.iso_code = v.iso_code
JOIN localization.address_template_field f ON f.field_key = v.field_key
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

DROP TABLE tmp_core_countries;
