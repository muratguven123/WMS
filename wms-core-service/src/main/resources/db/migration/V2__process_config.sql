-- =====================================================================
-- V2: WMS Process Configuration Schema
-- Lokasyon Bazlı Parametrik Süreç Konfigürasyonu
-- İş İsteri 3 — Flyway migration
-- =====================================================================

-- ---------------------------------------------------------------
-- ENUM TYPES
-- ---------------------------------------------------------------
CREATE TYPE error_strategy AS ENUM (
    'BLOCK',
    'BYPASS',
    'ROUTE_TO_QUARANTINE'
);

-- ---------------------------------------------------------------
-- 1. PROCESS_DEFINITIONS
-- İş akışı şablonları (INBOUND, OUTBOUND vb.)
-- ---------------------------------------------------------------
CREATE TABLE process_definitions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT uk_process_def_code UNIQUE (code)
);

-- ---------------------------------------------------------------
-- 2. PROCESS_STEP_DEFINITIONS
-- Süreç adımlarının sistem genelindeki şablonları (QC, SERIAL_CONTROL vb.)
-- ---------------------------------------------------------------
CREATE TABLE process_step_definitions (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    process_definition_id UUID         NOT NULL,
    code                  VARCHAR(50)  NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    default_sequence      INTEGER      NOT NULL,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,

    CONSTRAINT uk_step_def_process_code
        UNIQUE (process_definition_id, code),
    CONSTRAINT fk_step_def_process_def
        FOREIGN KEY (process_definition_id) REFERENCES process_definitions (id)
);

CREATE INDEX idx_step_def_process_def ON process_step_definitions (process_definition_id);

-- ---------------------------------------------------------------
-- 3. LOCATION_PROCESS_CONFIGS
-- Hangi lokasyonun hangi süreci aktif ettiğini tutar.
-- Bir lokasyon, aynı süreç tanımı için tek konfigürasyona sahip olabilir.
-- ---------------------------------------------------------------
CREATE TABLE location_process_configs (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id           UUID        NOT NULL,
    process_definition_id UUID        NOT NULL,
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,

    CONSTRAINT uk_loc_proc_config
        UNIQUE (location_id, process_definition_id),
    CONSTRAINT fk_loc_proc_config_process_def
        FOREIGN KEY (process_definition_id) REFERENCES process_definitions (id)
);

CREATE INDEX idx_loc_proc_config_location   ON location_process_configs (location_id);
CREATE INDEX idx_loc_proc_config_process    ON location_process_configs (process_definition_id);

-- ---------------------------------------------------------------
-- 4. LOCATION_PROCESS_STEP_CONFIGS
-- Lokasyon bazlı adım özelleştirmeleri.
-- Benzersizlik: Aynı akış içinde iki adım aynı sequence numarasına sahip olamaz.
-- ---------------------------------------------------------------
CREATE TABLE location_process_step_configs (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    location_process_config_id  UUID            NOT NULL,
    process_step_definition_id  UUID            NOT NULL,
    sequence                    INTEGER         NOT NULL,
    is_mandatory                BOOLEAN         NOT NULL DEFAULT TRUE,
    responsible_role_id         UUID,                             -- nullable
    requires_approval           BOOLEAN         NOT NULL DEFAULT FALSE,
    error_strategy              error_strategy  NOT NULL DEFAULT 'BLOCK',
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,

    CONSTRAINT uk_loc_step_config_seq
        UNIQUE (location_process_config_id, sequence),
    CONSTRAINT fk_step_config_loc_proc_config
        FOREIGN KEY (location_process_config_id) REFERENCES location_process_configs (id),
    CONSTRAINT fk_step_config_step_def
        FOREIGN KEY (process_step_definition_id) REFERENCES process_step_definitions (id)
);

CREATE INDEX idx_step_config_loc_proc_config ON location_process_step_configs (location_process_config_id);
CREATE INDEX idx_step_config_step_def        ON location_process_step_configs (process_step_definition_id);

-- ---------------------------------------------------------------
-- SEED DATA — Temel Süreç Tanımları
-- ---------------------------------------------------------------
INSERT INTO process_definitions (id, code, name) VALUES
    (gen_random_uuid(), 'INBOUND',  'Mal Kabul'),
    (gen_random_uuid(), 'OUTBOUND', 'Sevkiyat'),
    (gen_random_uuid(), 'RETURN',   'İade');

INSERT INTO process_step_definitions (id, process_definition_id, code, name, default_sequence)
SELECT
    gen_random_uuid(),
    pd.id,
    steps.code,
    steps.name,
    steps.seq
FROM process_definitions pd
JOIN (VALUES
    ('INBOUND',  'RECEIVING',        'Ürün Kabulü',          10),
    ('INBOUND',  'QC',               'Kalite Kontrol',        20),
    ('INBOUND',  'SERIAL_CONTROL',   'Seri No Kontrolü',      30),
    ('INBOUND',  'CUSTOMS_CONTROL',  'Gümrük Kontrolü',       40),
    ('INBOUND',  'PUTAWAY',          'Yerleştirme',           50),
    ('OUTBOUND', 'PICKING',          'Toplama',               10),
    ('OUTBOUND', 'PACKING',          'Paketleme',             20),
    ('OUTBOUND', 'QC',               'Kalite Kontrol',        30),
    ('OUTBOUND', 'SHIPPING',         'Yükleme',               40),
    ('RETURN',   'RECEIVING',        'İade Kabulü',           10),
    ('RETURN',   'QC',               'Kalite Kontrol',        20),
    ('RETURN',   'DECISION',         'İade Kararı',           30)
) AS steps(proc_code, code, name, seq) ON pd.code = steps.proc_code;
