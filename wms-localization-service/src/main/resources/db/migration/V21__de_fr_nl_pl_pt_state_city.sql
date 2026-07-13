-- =============================================================================
-- V21 — DE, FR, NL, PL, PT şablonlarına eyalet ve şehir alanları
--
-- Bu ülkelerde eyalet (state) ve şehir (city) elle girilen zorunlu alanlar
-- olarak eklenir; mevcut alanların sıralaması güncellenir.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE tmp_core_countries AS
SELECT * FROM dblink(
    'dbname=wms_core_db user=postgres password=postgres',
    'SELECT id, iso_code FROM countries'
) AS t(id BIGINT, iso_code VARCHAR(3));

-- Mevcut alanların sırasını kaydır (street → 3, house_no → 4, zip_code → 5)
UPDATE localization.country_address_template cat
SET sequence = v.new_seq
FROM tmp_core_countries cc,
     (VALUES
        ('DE', 'street',   3),
        ('DE', 'house_no', 4),
        ('DE', 'zip_code', 5),
        ('FR', 'street',   3),
        ('FR', 'house_no', 4),
        ('FR', 'zip_code', 5),
        ('NL', 'street',   3),
        ('NL', 'house_no', 4),
        ('NL', 'zip_code', 5),
        ('PL', 'street',   3),
        ('PL', 'house_no', 4),
        ('PL', 'zip_code', 5),
        ('PT', 'street',   3),
        ('PT', 'house_no', 4),
        ('PT', 'zip_code', 5)
     ) AS v(iso_code, field_key, new_seq)
JOIN localization.address_template_field f ON f.field_key = v.field_key
WHERE cat.country_id = cc.id
  AND cc.iso_code = v.iso_code
  AND cat.address_template_field_id = f.id;

-- Eyalet ve şehir ekle
INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT cc.id, f.id, v.is_mandatory, v.sequence,
       NULLIF(v.validation_regex, ''),
       v.error_message_key
FROM (VALUES
    ('DE', 'state', TRUE, 1, '', 'validation.state.required'),
    ('DE', 'city',  TRUE, 2, '', 'validation.city.required'),
    ('FR', 'state', TRUE, 1, '', 'validation.state.required'),
    ('FR', 'city',  TRUE, 2, '', 'validation.city.required'),
    ('NL', 'state', TRUE, 1, '', 'validation.state.required'),
    ('NL', 'city',  TRUE, 2, '', 'validation.city.required'),
    ('PL', 'state', TRUE, 1, '', 'validation.state.required'),
    ('PL', 'city',  TRUE, 2, '', 'validation.city.required'),
    ('PT', 'state', TRUE, 1, '', 'validation.state.required'),
    ('PT', 'city',  TRUE, 2, '', 'validation.city.required')
) AS v(iso_code, field_key, is_mandatory, sequence, validation_regex, error_message_key)
JOIN tmp_core_countries cc ON cc.iso_code = v.iso_code
JOIN localization.address_template_field f ON f.field_key = v.field_key
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

DROP TABLE tmp_core_countries;
