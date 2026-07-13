-- =============================================================================
-- V16 — 15 ülke için country_address_template seed'i
--
-- WMS_Dinamik_Adres_Plani_v3/v4'te onaylanan matrise göre. TR/US zaten
-- V12_2'de seed edilmiş (dokunulmuyor). Bu migration DE'yi tamamlar ve
-- ES/FR/IT/PT/RU/SA(ar)/CN(zh)/JP/KR/IN/NL/PL için şablon ekler.
--
-- country_id, cross-database bir referans olduğundan (wms-core-service'in
-- countries tablosu), V14'teki gibi dblink ile iso_code üzerinden çözülür —
-- literal id varsayımı YAPILMAZ (core tarafında id'ler auto IDENTITY ile
-- üretiliyor, V18__address_countries_expansion.sql'in önce çalışmış olması
-- gerekir).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TEMP TABLE tmp_core_countries AS
SELECT * FROM dblink(
    'dbname=wms_core_db user=postgres password=postgres',
    'SELECT id, iso_code FROM countries'
) AS t(id BIGINT, iso_code VARCHAR(3));

-- ---------------------------------------------------------------------------
-- Şablon satırları — (iso_code, field_key, is_mandatory, sequence, validation_regex, error_message_key)
-- validation_regex boş string ise NULL'a çevrilir (opsiyonel alan, sadece format kontrolü yoksa).
-- ---------------------------------------------------------------------------
INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT cc.id, f.id, v.is_mandatory, v.sequence,
       NULLIF(v.validation_regex, ''),
       v.error_message_key
FROM (VALUES
    -- DE (V12_2'de eksikti, burada tamamlanıyor)
    ('DE', 'street',   TRUE,  1, '',                              'validation.street.required'),
    ('DE', 'house_no', TRUE,  2, '',                              'validation.house_no.required'),
    ('DE', 'zip_code', FALSE, 3, '^[0-9]{5}$',                    'validation.zipCode.invalid'),

    -- ES — İspanya
    ('ES', 'state',        TRUE,  1, '',                          'validation.state.required'),
    ('ES', 'street',       TRUE,  2, '',                          'validation.street.required'),
    ('ES', 'house_no',     TRUE,  3, '',                          'validation.house_no.required'),
    ('ES', 'floor',        FALSE, 4, '',                          NULL),
    ('ES', 'apartment_no', FALSE, 5, '',                          NULL),
    ('ES', 'zip_code',     FALSE, 6, '^[0-9]{5}$',                'validation.zipCode.invalid'),

    -- FR — Fransa
    ('FR', 'street',   TRUE,  1, '',                              'validation.street.required'),
    ('FR', 'house_no', TRUE,  2, '',                              'validation.house_no.required'),
    ('FR', 'zip_code', FALSE, 3, '^[0-9]{5}$',                    'validation.zipCode.invalid'),

    -- IT — İtalya (state = il kısaltması, örn. RM)
    ('IT', 'state',    TRUE,  1, '',                              'validation.state.required'),
    ('IT', 'street',   TRUE,  2, '',                              'validation.street.required'),
    ('IT', 'house_no', TRUE,  3, '',                              'validation.house_no.required'),
    ('IT', 'zip_code', FALSE, 4, '^[0-9]{5}$',                    'validation.zipCode.invalid'),

    -- PT — Portekiz
    ('PT', 'street',   TRUE,  1, '',                              'validation.street.required'),
    ('PT', 'house_no', TRUE,  2, '',                              'validation.house_no.required'),
    ('PT', 'zip_code', FALSE, 3, '^[0-9]{4}-[0-9]{3}$',           'validation.zipCode.invalid'),

    -- RU — Rusya
    ('RU', 'state',        TRUE,  1, '',                          'validation.state.required'),
    ('RU', 'street',       TRUE,  2, '',                          'validation.street.required'),
    ('RU', 'house_no',     TRUE,  3, '',                          'validation.house_no.required'),
    ('RU', 'korpus',       FALSE, 4, '',                          NULL),
    ('RU', 'apartment_no', FALSE, 5, '',                          NULL),
    ('RU', 'zip_code',     FALSE, 6, '^[0-9]{6}$',                'validation.zipCode.invalid'),

    -- SA — Suudi Arabistan (ar)
    ('SA', 'state',         TRUE,  1, '',                         'validation.state.required'),
    ('SA', 'district',      TRUE,  2, '',                         'validation.district.required'),
    ('SA', 'street',        TRUE,  3, '',                         'validation.street.required'),
    ('SA', 'house_no',      TRUE,  4, '',                         'validation.house_no.required'),
    ('SA', 'additional_no', FALSE, 5, '^[0-9]{4}$',               'validation.additional_no.invalid'),
    ('SA', 'zip_code',      FALSE, 6, '^[0-9]{5}$',               'validation.zipCode.invalid'),

    -- CN — Çin, Anakara (zh)
    ('CN', 'state',    TRUE,  1, '',                              'validation.state.required'),
    ('CN', 'district', TRUE,  2, '',                              'validation.district.required'),
    ('CN', 'street',   TRUE,  3, '',                              'validation.street.required'),
    ('CN', 'zip_code', FALSE, 4, '^[0-9]{6}$',                    'validation.zipCode.invalid'),

    -- JP — Japonya
    ('JP', 'state',         TRUE,  1, '',                         'validation.state.required'),
    ('JP', 'chome_banchi',  TRUE,  2, '',                         'validation.chome_banchi.required'),
    ('JP', 'building_name', FALSE, 3, '',                         NULL),
    ('JP', 'zip_code',      FALSE, 4, '^[0-9]{3}-[0-9]{4}$',      'validation.zipCode.invalid'),

    -- KR — Güney Kore
    ('KR', 'state',     TRUE,  1, '',                             'validation.state.required'),
    ('KR', 'district',  TRUE,  2, '',                             'validation.district.required'),
    ('KR', 'road_name', TRUE,  3, '',                             'validation.road_name.required'),
    ('KR', 'house_no',  FALSE, 4, '',                             NULL),
    ('KR', 'zip_code',  FALSE, 5, '^[0-9]{5}$',                   'validation.zipCode.invalid'),

    -- IN — Hindistan (hi)
    ('IN', 'state',    TRUE,  1, '',                              'validation.state.required'),
    ('IN', 'street',   TRUE,  2, '',                              'validation.street.required'),
    ('IN', 'locality', FALSE, 3, '',                              NULL),
    ('IN', 'landmark', FALSE, 4, '',                              NULL),
    ('IN', 'zip_code', FALSE, 5, '^[0-9]{6}$',                    'validation.zipCode.invalid'),

    -- NL — Hollanda
    ('NL', 'street',   TRUE,  1, '',                              'validation.street.required'),
    ('NL', 'house_no', TRUE,  2, '',                              'validation.house_no.required'),
    ('NL', 'zip_code', FALSE, 3, '^[0-9]{4}\s?[A-Z]{2}$',         'validation.zipCode.invalid'),

    -- PL — Polonya
    ('PL', 'street',   TRUE,  1, '',                              'validation.street.required'),
    ('PL', 'house_no', TRUE,  2, '',                              'validation.house_no.required'),
    ('PL', 'zip_code', FALSE, 3, '^[0-9]{2}-[0-9]{3}$',           'validation.zipCode.invalid')
) AS v(iso_code, field_key, is_mandatory, sequence, validation_regex, error_message_key)
JOIN tmp_core_countries cc ON cc.iso_code = v.iso_code
JOIN localization.address_template_field f ON f.field_key = v.field_key
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

DO $$
DECLARE
    v_expected CONSTANT int := 13;
    v_found int;
BEGIN
    SELECT COUNT(*) INTO v_found
    FROM tmp_core_countries
    WHERE iso_code IN ('DE','ES','FR','IT','PT','RU','SA','CN','JP','KR','IN','NL','PL');

    IF v_found < v_expected THEN
        RAISE WARNING
            'V16 country_address_template seed: expected % core countries, found % — seed may be incomplete. Ensure wms_core_db V18 ran first.',
            v_expected, v_found;
    END IF;
END $$;

DROP TABLE tmp_core_countries;
