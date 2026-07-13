-- =============================================================================
-- V4_5 — UUID → BIGINT migration (wms_integration_db)
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
    'host=localhost dbname=wms_core_db user=postgres password=postgres',
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
ALTER TABLE location_integration_configs DROP CONSTRAINT IF EXISTS fk_loc_int_cfg_system;
ALTER TABLE integration_logs DROP CONSTRAINT IF EXISTS fk_int_log_config;
ALTER TABLE integration_logs DROP CONSTRAINT IF EXISTS fk_int_log_job;

DROP INDEX IF EXISTS idx_loc_int_cfg_active_unique;

-- ---------------------------------------------------------------------------
-- Migrate PKs (parent first)
-- ---------------------------------------------------------------------------
ALTER TABLE integration_systems ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY code, id) AS rn
    FROM integration_systems
)
UPDATE integration_systems s
SET id_bigint = r.rn
FROM ranked r
WHERE s.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'integration_system', id, id_bigint FROM integration_systems ON CONFLICT DO NOTHING;

ALTER TABLE integration_jobs ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY code, id) AS rn
    FROM integration_jobs
)
UPDATE integration_jobs j
SET id_bigint = r.rn
FROM ranked r
WHERE j.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'integration_job', id, id_bigint FROM integration_jobs ON CONFLICT DO NOTHING;

ALTER TABLE location_integration_configs ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM location_integration_configs
)
UPDATE location_integration_configs c
SET id_bigint = r.rn
FROM ranked r
WHERE c.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'location_integration_config', id, id_bigint FROM location_integration_configs ON CONFLICT DO NOTHING;

ALTER TABLE integration_logs ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM integration_logs
)
UPDATE integration_logs l
SET id_bigint = r.rn
FROM ranked r
WHERE l.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'integration_log', id, id_bigint FROM integration_logs ON CONFLICT DO NOTHING;

ALTER TABLE outbox_messages ADD COLUMN id_bigint BIGINT;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM outbox_messages
)
UPDATE outbox_messages o
SET id_bigint = r.rn
FROM ranked r
WHERE o.id = r.id;

INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT 'outbox_message', id, id_bigint FROM outbox_messages ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Migrate FK / cross-service columns
-- ---------------------------------------------------------------------------
ALTER TABLE location_integration_configs
    ADD COLUMN location_id_bigint BIGINT,
    ADD COLUMN integration_system_id_bigint BIGINT;

UPDATE location_integration_configs c
SET location_id_bigint = map_uuid('location', c.location_id),
    integration_system_id_bigint = map_uuid('integration_system', c.integration_system_id);

ALTER TABLE integration_logs
    ADD COLUMN location_integration_config_id_bigint BIGINT,
    ADD COLUMN integration_job_id_bigint BIGINT,
    ADD COLUMN outbox_message_id_bigint BIGINT;

UPDATE integration_logs l
SET location_integration_config_id_bigint = map_uuid('location_integration_config', l.location_integration_config_id),
    integration_job_id_bigint = map_uuid('integration_job', l.integration_job_id),
    outbox_message_id_bigint = map_uuid('outbox_message', l.outbox_message_id);

ALTER TABLE outbox_messages
    ADD COLUMN aggregate_id_bigint BIGINT,
    ADD COLUMN location_id_bigint BIGINT;

UPDATE outbox_messages o
SET location_id_bigint = map_uuid('location', o.location_id);

-- aggregate_id maps to various external aggregates — preserve via opaque per-type mapping
INSERT INTO id_legacy_map (entity_type, old_uuid, new_id)
SELECT entity_type, old_uuid, new_id
FROM (
    SELECT
        'agg_' || lower(aggregate_type) AS entity_type,
        aggregate_id AS old_uuid,
        ROW_NUMBER() OVER (
            PARTITION BY lower(aggregate_type)
            ORDER BY aggregate_id
        ) AS new_id
    FROM outbox_messages
    WHERE aggregate_id IS NOT NULL
) mapped
ON CONFLICT DO NOTHING;

UPDATE outbox_messages o
SET aggregate_id_bigint = m.new_id
FROM id_legacy_map m
WHERE m.entity_type = 'agg_' || lower(o.aggregate_type)
  AND m.old_uuid = o.aggregate_id;

-- ---------------------------------------------------------------------------
-- Swap columns
-- ---------------------------------------------------------------------------
ALTER TABLE integration_systems DROP CONSTRAINT pk_integration_systems;
ALTER TABLE integration_systems DROP COLUMN id;
ALTER TABLE integration_systems RENAME COLUMN id_bigint TO id;
ALTER TABLE integration_systems ADD CONSTRAINT pk_integration_systems PRIMARY KEY (id);

ALTER TABLE integration_jobs DROP CONSTRAINT pk_integration_jobs;
ALTER TABLE integration_jobs DROP COLUMN id;
ALTER TABLE integration_jobs RENAME COLUMN id_bigint TO id;
ALTER TABLE integration_jobs ADD CONSTRAINT pk_integration_jobs PRIMARY KEY (id);

ALTER TABLE location_integration_configs DROP CONSTRAINT pk_location_integration_configs;
ALTER TABLE location_integration_configs DROP COLUMN id;
ALTER TABLE location_integration_configs RENAME COLUMN id_bigint TO id;
ALTER TABLE location_integration_configs DROP COLUMN location_id, DROP COLUMN integration_system_id;
ALTER TABLE location_integration_configs RENAME COLUMN location_id_bigint TO location_id;
ALTER TABLE location_integration_configs RENAME COLUMN integration_system_id_bigint TO integration_system_id;
ALTER TABLE location_integration_configs ADD CONSTRAINT pk_location_integration_configs PRIMARY KEY (id);

ALTER TABLE integration_logs DROP CONSTRAINT pk_integration_logs;
ALTER TABLE integration_logs DROP COLUMN id;
ALTER TABLE integration_logs RENAME COLUMN id_bigint TO id;
ALTER TABLE integration_logs
    DROP COLUMN location_integration_config_id,
    DROP COLUMN integration_job_id,
    DROP COLUMN outbox_message_id;
ALTER TABLE integration_logs RENAME COLUMN location_integration_config_id_bigint TO location_integration_config_id;
ALTER TABLE integration_logs RENAME COLUMN integration_job_id_bigint TO integration_job_id;
ALTER TABLE integration_logs RENAME COLUMN outbox_message_id_bigint TO outbox_message_id;
ALTER TABLE integration_logs ADD CONSTRAINT pk_integration_logs PRIMARY KEY (id);

ALTER TABLE outbox_messages DROP CONSTRAINT pk_outbox_messages;
ALTER TABLE outbox_messages DROP COLUMN id;
ALTER TABLE outbox_messages RENAME COLUMN id_bigint TO id;
ALTER TABLE outbox_messages DROP COLUMN aggregate_id, DROP COLUMN location_id;
ALTER TABLE outbox_messages RENAME COLUMN aggregate_id_bigint TO aggregate_id;
ALTER TABLE outbox_messages RENAME COLUMN location_id_bigint TO location_id;
ALTER TABLE outbox_messages ADD CONSTRAINT pk_outbox_messages PRIMARY KEY (id);

-- ---------------------------------------------------------------------------
-- Recreate FK constraints and indexes
-- ---------------------------------------------------------------------------
ALTER TABLE location_integration_configs
    ADD CONSTRAINT fk_loc_int_cfg_system
        FOREIGN KEY (integration_system_id) REFERENCES integration_systems (id);

ALTER TABLE integration_logs
    ADD CONSTRAINT fk_int_log_config
        FOREIGN KEY (location_integration_config_id) REFERENCES location_integration_configs (id),
    ADD CONSTRAINT fk_int_log_job
        FOREIGN KEY (integration_job_id) REFERENCES integration_jobs (id);

CREATE UNIQUE INDEX idx_loc_int_cfg_active_unique
    ON location_integration_configs (location_id)
    WHERE is_active = TRUE;

-- ---------------------------------------------------------------------------
-- BIGINT IDENTITY
-- ---------------------------------------------------------------------------
ALTER TABLE integration_systems ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('integration_systems', 'id'), COALESCE((SELECT MAX(id) FROM integration_systems), 1));

ALTER TABLE integration_jobs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('integration_jobs', 'id'), COALESCE((SELECT MAX(id) FROM integration_jobs), 1));

ALTER TABLE location_integration_configs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('location_integration_configs', 'id'), COALESCE((SELECT MAX(id) FROM location_integration_configs), 1));

ALTER TABLE integration_logs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('integration_logs', 'id'), COALESCE((SELECT MAX(id) FROM integration_logs), 1));

ALTER TABLE outbox_messages ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
SELECT setval(pg_get_serial_sequence('outbox_messages', 'id'), COALESCE((SELECT MAX(id) FROM outbox_messages), 1));

DROP FUNCTION map_uuid(VARCHAR, UUID);

COMMIT;
