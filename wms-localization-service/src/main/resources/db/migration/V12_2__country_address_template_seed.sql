-- =============================================================================
-- V12.2 — Ülke adres şablonu demo seed (TR + US)
-- country UUID'leri wms-core V9 address demo seed ile hizalıdır.
-- =============================================================================

INSERT INTO localization.address_template_field (field_key, field_label_key)
VALUES ('zip_code', 'fields.zip_code')
ON CONFLICT (field_key) DO NOTHING;

-- Türkiye (aaaaaaaa-0000-0000-0000-000000000001)
INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT 'aaaaaaaa-0000-0000-0000-000000000001'::uuid, atf.id, TRUE, 1, NULL, 'validation.district.required'
FROM localization.address_template_field atf WHERE atf.field_key = 'district'
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT 'aaaaaaaa-0000-0000-0000-000000000001'::uuid, atf.id, TRUE, 2, NULL, 'validation.neighborhood.required'
FROM localization.address_template_field atf WHERE atf.field_key = 'neighborhood'
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT 'aaaaaaaa-0000-0000-0000-000000000001'::uuid, atf.id, FALSE, 3, '^[0-9]{5}$', 'validation.zipCode.invalid'
FROM localization.address_template_field atf WHERE atf.field_key = 'zip_code'
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

-- ABD (aaaaaaaa-0000-0000-0000-000000000010)
INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT 'aaaaaaaa-0000-0000-0000-000000000010'::uuid, atf.id, TRUE, 1, NULL, 'validation.street.required'
FROM localization.address_template_field atf WHERE atf.field_key = 'street'
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;

INSERT INTO localization.country_address_template
    (country_id, address_template_field_id, is_mandatory, sequence, validation_regex, error_message_key)
SELECT 'aaaaaaaa-0000-0000-0000-000000000010'::uuid, atf.id, FALSE, 2, '^[0-9]{5}(-[0-9]{4})?$', 'validation.zipCode.invalid'
FROM localization.address_template_field atf WHERE atf.field_key = 'zip_code'
ON CONFLICT (country_id, address_template_field_id) DO NOTHING;
