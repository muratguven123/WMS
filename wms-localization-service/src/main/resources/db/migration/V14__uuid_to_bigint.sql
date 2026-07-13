-- =============================================================================
-- V14 — UUID → BIGINT migration (wms_localization_db)
-- Requires wms_core_db V16__uuid_to_bigint.sql to have run first.
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TABLE id_legacy_map (
    entity_type VARCHAR(50) NOT NULL,
    old_uuid    UUID        NOT NULL,
    new_id      BIGINT      NOT NULL,
    PRIMARY KEY (entity_type, old_uuid)
);

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT entity_type, old_uuid, new_id
FROM dblink(
    'dbname=wms_core_db user=postgres password=postgres',
    'SELECT entity_type, old_uuid, new_id FROM id_legacy_map'
) AS t(entity_type VARCHAR(50), old_uuid UUID, new_id BIGINT);

CREATE OR REPLACE FUNCTION map_uuid(p_entity_type VARCHAR, p_uuid UUID)
RETURNS BIGINT
LANGUAGE sql
STABLE
AS $$
    SELECT m.new_id
    FROM id_legacy_map m
    WHERE m.entity_type = p_entity_type
      AND m.old_uuid = p_uuid;
$$;

-- ---------------------------------------------------------------------------
-- Drop FK constraints
-- ---------------------------------------------------------------------------
ALTER TABLE translation_value DROP CONSTRAINT IF EXISTS fk_translation_value_language;
ALTER TABLE translation_value DROP CONSTRAINT IF EXISTS fk_translation_value_key;
ALTER TABLE localization.country_address_template DROP CONSTRAINT IF EXISTS fk_cat_template_field;

-- ---------------------------------------------------------------------------
-- Migrate PKs — localization.address_template_field first (no deps)
-- ---------------------------------------------------------------------------
ALTER TABLE localization.address_template_field ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY field_key, id) AS rn
    FROM localization.address_template_field
)
UPDATE localization.address_template_field f
SET id_bigint = r.rn
FROM ranked r
WHERE f.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'address_template_field', id, id_bigint
FROM localization.address_template_field
ON CONFLICT DO NOTHING;

ALTER TABLE language ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY code, id) AS rn
    FROM language
)
UPDATE language l
SET id_bigint = r.rn
FROM ranked r
WHERE l.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'language', id, id_bigint FROM language ON CONFLICT DO NOTHING;

ALTER TABLE translation_key ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY key_code, id) AS rn
    FROM translation_key
)
UPDATE translation_key t
SET id_bigint = r.rn
FROM ranked r
WHERE t.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'translation_key', id, id_bigint FROM translation_key ON CONFLICT DO NOTHING;

ALTER TABLE translation_value ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY language_id, translation_key_id, id) AS rn
    FROM translation_value
)
UPDATE translation_value t
SET id_bigint = r.rn
FROM ranked r
WHERE t.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'translation_value', id, id_bigint FROM translation_value ON CONFLICT DO NOTHING;

ALTER TABLE missing_translation_log ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY first_seen_at, id) AS rn
    FROM missing_translation_log
)
UPDATE missing_translation_log m
SET id_bigint = r.rn
FROM ranked r
WHERE m.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'missing_translation_log', id, id_bigint FROM missing_translation_log ON CONFLICT DO NOTHING;

ALTER TABLE country_format_config ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY country_id, id) AS rn
    FROM country_format_config
)
UPDATE country_format_config c
SET id_bigint = r.rn
FROM ranked r
WHERE c.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'country_format_config', id, id_bigint FROM country_format_config ON CONFLICT DO NOTHING;

ALTER TABLE location_format_override ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY location_id, id) AS rn
    FROM location_format_override
)
UPDATE location_format_override l
SET id_bigint = r.rn
FROM ranked r
WHERE l.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'location_format_override', id, id_bigint FROM location_format_override ON CONFLICT DO NOTHING;

ALTER TABLE localization.country_address_template ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY country_id, sequence, id) AS rn
    FROM localization.country_address_template
)
UPDATE localization.country_address_template c
SET id_bigint = r.rn
FROM ranked r
WHERE c.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'country_address_template', id, id_bigint
FROM localization.country_address_template
ON CONFLICT DO NOTHING;

ALTER TABLE localization.address ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY updated_at, id) AS rn
    FROM localization.address
)
UPDATE localization.address a
SET id_bigint = r.rn
FROM ranked r
WHERE a.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'address', id, id_bigint FROM localization.address ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Migrate FK / cross-service columns
-- ---------------------------------------------------------------------------
ALTER TABLE translation_value
    ADD COLUMN language_id_bigint BIGINT,
    ADD COLUMN translation_key_id_bigint BIGINT;

UPDATE translation_value t
SET language_id_bigint = map_uuid('language', t.language_id),
    translation_key_id_bigint = map_uuid('translation_key', t.translation_key_id);

ALTER TABLE country_format_config ADD COLUMN country_id_bigint BIGINT;

UPDATE country_format_config c
SET country_id_bigint = map_uuid('country', c.country_id);

ALTER TABLE location_format_override ADD COLUMN location_id_bigint BIGINT;

UPDATE location_format_override l
SET location_id_bigint = map_uuid('location', l.location_id);

ALTER TABLE localization.country_address_template
    ADD COLUMN country_id_bigint BIGINT,
    ADD COLUMN address_template_field_id_bigint BIGINT;

UPDATE localization.country_address_template c
SET country_id_bigint = map_uuid('country', c.country_id),
    address_template_field_id_bigint = map_uuid('address_template_field', c.address_template_field_id);

ALTER TABLE localization.address ADD COLUMN country_id_bigint BIGINT;

UPDATE localization.address a
SET country_id_bigint = map_uuid('country', a.country_id);

-- ---------------------------------------------------------------------------
-- Swap columns — address_template_field
-- ---------------------------------------------------------------------------
ALTER TABLE localization.address_template_field DROP CONSTRAINT pk_address_template_field;
ALTER TABLE localization.address_template_field DROP COLUMN id;
ALTER TABLE localization.address_template_field RENAME COLUMN id_bigint TO id;
ALTER TABLE localization.address_template_field ADD CONSTRAINT pk_address_template_field PRIMARY KEY (id);

-- language
ALTER TABLE language DROP CONSTRAINT pk_language;
ALTER TABLE language DROP COLUMN id;
ALTER TABLE language RENAME COLUMN id_bigint TO id;
ALTER TABLE language ADD CONSTRAINT pk_language PRIMARY KEY (id);

-- translation_key
ALTER TABLE translation_key DROP CONSTRAINT pk_translation_key;
ALTER TABLE translation_key DROP COLUMN id;
ALTER TABLE translation_key RENAME COLUMN id_bigint TO id;
ALTER TABLE translation_key ADD CONSTRAINT pk_translation_key PRIMARY KEY (id);

-- translation_value
ALTER TABLE translation_value DROP CONSTRAINT pk_translation_value;
ALTER TABLE translation_value DROP CONSTRAINT uq_translation_value_lang_key;
ALTER TABLE translation_value DROP COLUMN id;
ALTER TABLE translation_value RENAME COLUMN id_bigint TO id;
ALTER TABLE translation_value DROP COLUMN language_id, DROP COLUMN translation_key_id;
ALTER TABLE translation_value RENAME COLUMN language_id_bigint TO language_id;
ALTER TABLE translation_value RENAME COLUMN translation_key_id_bigint TO translation_key_id;
ALTER TABLE translation_value ADD CONSTRAINT pk_translation_value PRIMARY KEY (id);

-- missing_translation_log
ALTER TABLE missing_translation_log DROP CONSTRAINT pk_missing_translation_log;
ALTER TABLE missing_translation_log DROP COLUMN id;
ALTER TABLE missing_translation_log RENAME COLUMN id_bigint TO id;
ALTER TABLE missing_translation_log ADD CONSTRAINT pk_missing_translation_log PRIMARY KEY (id);

-- country_format_config
ALTER TABLE country_format_config DROP CONSTRAINT pk_country_format_config;
ALTER TABLE country_format_config DROP CONSTRAINT uq_cfc_country_id;
ALTER TABLE country_format_config DROP COLUMN id;
ALTER TABLE country_format_config RENAME COLUMN id_bigint TO id;
ALTER TABLE country_format_config DROP COLUMN country_id;
ALTER TABLE country_format_config RENAME COLUMN country_id_bigint TO country_id;
ALTER TABLE country_format_config ADD CONSTRAINT pk_country_format_config PRIMARY KEY (id);

-- location_format_override
ALTER TABLE location_format_override DROP CONSTRAINT pk_location_format_override;
ALTER TABLE location_format_override DROP CONSTRAINT uq_lfo_location_id;
ALTER TABLE location_format_override DROP COLUMN id;
ALTER TABLE location_format_override RENAME COLUMN id_bigint TO id;
ALTER TABLE location_format_override DROP COLUMN location_id;
ALTER TABLE location_format_override RENAME COLUMN location_id_bigint TO location_id;
ALTER TABLE location_format_override ADD CONSTRAINT pk_location_format_override PRIMARY KEY (id);

-- country_address_template
ALTER TABLE localization.country_address_template DROP CONSTRAINT pk_country_address_template;
ALTER TABLE localization.country_address_template DROP CONSTRAINT uq_cat_country_field;
ALTER TABLE localization.country_address_template DROP COLUMN id;
ALTER TABLE localization.country_address_template RENAME COLUMN id_bigint TO id;
ALTER TABLE localization.country_address_template
    DROP COLUMN country_id, DROP COLUMN address_template_field_id;
ALTER TABLE localization.country_address_template RENAME COLUMN country_id_bigint TO country_id;
ALTER TABLE localization.country_address_template
    RENAME COLUMN address_template_field_id_bigint TO address_template_field_id;
ALTER TABLE localization.country_address_template ADD CONSTRAINT pk_country_address_template PRIMARY KEY (id);

-- address
ALTER TABLE localization.address DROP CONSTRAINT pk_address;
ALTER TABLE localization.address DROP COLUMN id;
ALTER TABLE localization.address RENAME COLUMN id_bigint TO id;
ALTER TABLE localization.address DROP COLUMN country_id;
ALTER TABLE localization.address RENAME COLUMN country_id_bigint TO country_id;
ALTER TABLE localization.address ADD CONSTRAINT pk_address PRIMARY KEY (id);

-- ---------------------------------------------------------------------------
-- Recreate FK constraints and unique indexes
-- ---------------------------------------------------------------------------
ALTER TABLE translation_value
    ADD CONSTRAINT uq_translation_value_lang_key UNIQUE (language_id, translation_key_id),
    ADD CONSTRAINT fk_translation_value_language
        FOREIGN KEY (language_id) REFERENCES language (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_translation_value_key
        FOREIGN KEY (translation_key_id) REFERENCES translation_key (id) ON DELETE CASCADE;

ALTER TABLE country_format_config
    ADD CONSTRAINT uq_cfc_country_id UNIQUE (country_id);

ALTER TABLE location_format_override
    ADD CONSTRAINT uq_lfo_location_id UNIQUE (location_id);

ALTER TABLE localization.country_address_template
    ADD CONSTRAINT fk_cat_template_field
        FOREIGN KEY (address_template_field_id)
        REFERENCES localization.address_template_field (id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_cat_country_field UNIQUE (country_id, address_template_field_id);

-- ---------------------------------------------------------------------------
-- BIGINT IDENTITY
-- ---------------------------------------------------------------------------
ALTER TABLE localization.address_template_field ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('localization.address_template_field', 'id'),
    COALESCE((SELECT MAX(id) FROM localization.address_template_field), 1));

ALTER TABLE language ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('language', 'id'), COALESCE((SELECT MAX(id) FROM language), 1));

ALTER TABLE translation_key ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('translation_key', 'id'), COALESCE((SELECT MAX(id) FROM translation_key), 1));

ALTER TABLE translation_value ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('translation_value', 'id'), COALESCE((SELECT MAX(id) FROM translation_value), 1));

ALTER TABLE missing_translation_log ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('missing_translation_log', 'id'),
    COALESCE((SELECT MAX(id) FROM missing_translation_log), 1));

ALTER TABLE country_format_config ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('country_format_config', 'id'),
    COALESCE((SELECT MAX(id) FROM country_format_config), 1));

ALTER TABLE location_format_override ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('location_format_override', 'id'),
    COALESCE((SELECT MAX(id) FROM location_format_override), 1));

ALTER TABLE localization.country_address_template ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('localization.country_address_template', 'id'),
    COALESCE((SELECT MAX(id) FROM localization.country_address_template), 1));

ALTER TABLE localization.address ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('localization.address', 'id'),
    COALESCE((SELECT MAX(id) FROM localization.address), 1));

DROP FUNCTION map_uuid(VARCHAR, UUID);

COMMIT;
