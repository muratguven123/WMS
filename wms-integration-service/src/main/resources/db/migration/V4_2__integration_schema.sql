-- =============================================================================
-- WMS Integration Service — Faz 4.2 DDL
-- Veritabanı: wms_integration_db (PostgreSQL 15+)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. integration_systems
--    Desteklenen ERP / muhasebe sistemi tanımları.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS integration_systems (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(30) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT pk_integration_systems       PRIMARY KEY (id),
    CONSTRAINT uq_integration_systems_code  UNIQUE      (code)
);

CREATE INDEX IF NOT EXISTS idx_integration_system_code
    ON integration_systems (code);

COMMENT ON TABLE  integration_systems        IS 'Desteklenen ERP / muhasebe sistemi tanımları (SAP, ORACLE, LOGO, MIKRO vb.)';
COMMENT ON COLUMN integration_systems.code   IS 'Spring adapter bean adıyla örtüşen benzersiz ERP kodu';

-- ---------------------------------------------------------------------------
-- 2. location_integration_configs
--    Depo (lokasyon) bazında ERP bağlantı konfigürasyonu.
--    Her lokasyon için en fazla 1 aktif config: partial unique index ile sağlanır.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS location_integration_configs (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    location_id             UUID        NOT NULL,
    integration_system_id   UUID        NOT NULL,
    connection_type         VARCHAR(10) NOT NULL,   -- REST | SOAP | SFTP | DB
    connection_params       JSONB,                  -- bağlantı parametreleri
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ,

    CONSTRAINT pk_location_integration_configs
        PRIMARY KEY (id),
    CONSTRAINT fk_loc_int_cfg_system
        FOREIGN KEY (integration_system_id) REFERENCES integration_systems(id),
    CONSTRAINT chk_connection_type
        CHECK (connection_type IN ('REST','SOAP','SFTP','DB'))
);

-- Her lokasyon için yalnızca 1 aktif config (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS idx_loc_int_cfg_active_unique
    ON location_integration_configs (location_id)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_loc_int_cfg_location_id
    ON location_integration_configs (location_id);

CREATE INDEX IF NOT EXISTS idx_loc_int_cfg_system_id
    ON location_integration_configs (integration_system_id);

CREATE INDEX IF NOT EXISTS idx_loc_int_cfg_is_active
    ON location_integration_configs (is_active);

-- JSONB GIN indeksi: connectionParams içinde alan bazlı sorgular için
CREATE INDEX IF NOT EXISTS idx_loc_int_cfg_params_gin
    ON location_integration_configs USING GIN (connection_params);

COMMENT ON TABLE  location_integration_configs                   IS 'Lokasyon bazında ERP bağlantı konfigürasyonu';
COMMENT ON COLUMN location_integration_configs.location_id       IS 'wms-core-service Location.id — FK değil, mikroservis sınırı korunur';
COMMENT ON COLUMN location_integration_configs.connection_params IS 'JSONB: baseUrl, apiKey, host, remoteDir vb. bağlantı parametreleri';

-- ---------------------------------------------------------------------------
-- 3. integration_jobs
--    Tanımlı entegrasyon iş tipleri (MAT_SYNC, STOCK_MOVE, INVOICE_SYNC vb.)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS integration_jobs (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    direction   VARCHAR(10) NOT NULL,   -- INBOUND | OUTBOUND
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT pk_integration_jobs       PRIMARY KEY (id),
    CONSTRAINT uq_integration_jobs_code  UNIQUE      (code),
    CONSTRAINT chk_direction             CHECK (direction IN ('INBOUND','OUTBOUND'))
);

CREATE INDEX IF NOT EXISTS idx_integration_job_code
    ON integration_jobs (code);

COMMENT ON TABLE  integration_jobs           IS 'Entegrasyon iş tipleri tanım tablosu';
COMMENT ON COLUMN integration_jobs.code      IS 'Outbox Worker ve servis katmanında kullanılan benzersiz iş kodu';
COMMENT ON COLUMN integration_jobs.direction IS 'OUTBOUND: WMS→ERP, INBOUND: ERP→WMS';

-- ---------------------------------------------------------------------------
-- 4. integration_logs
--    Her entegrasyon denemesinin detaylı kayıt defteri.
--    Büyük tablodur; zaman bazlı partitioning ilerleyen aşamada planlanmalıdır.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS integration_logs (
    id                              UUID        NOT NULL DEFAULT gen_random_uuid(),
    location_integration_config_id  UUID        NOT NULL,
    integration_job_id              UUID        NOT NULL,
    status                          VARCHAR(10) NOT NULL,   -- SUCCESS | FAILED | RETRYING
    request_payload                 TEXT,
    response_payload                TEXT,
    error_message                   TEXT,
    external_reference              VARCHAR(200),
    retry_count                     INTEGER     NOT NULL DEFAULT 0,
    outbox_message_id               UUID,
    last_attempt_at                 TIMESTAMPTZ,
    -- BaseEntity alanları
    is_active                       BOOLEAN,               -- log'da anlamsız; null kabul edilir
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ,           -- log'da kullanılmaz

    CONSTRAINT pk_integration_logs PRIMARY KEY (id),
    CONSTRAINT fk_int_log_config
        FOREIGN KEY (location_integration_config_id)
        REFERENCES location_integration_configs(id),
    CONSTRAINT fk_int_log_job
        FOREIGN KEY (integration_job_id)
        REFERENCES integration_jobs(id),
    CONSTRAINT chk_int_log_status
        CHECK (status IN ('SUCCESS','FAILED','RETRYING')),
    CONSTRAINT chk_retry_count_positive
        CHECK (retry_count >= 0)
);

-- Performans indeksleri
CREATE INDEX IF NOT EXISTS idx_int_log_status
    ON integration_logs (status);

CREATE INDEX IF NOT EXISTS idx_int_log_created_at
    ON integration_logs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_int_log_config_id
    ON integration_logs (location_integration_config_id);

-- Outbox Worker bileşik sorgu indeksi: status + retry_count
CREATE INDEX IF NOT EXISTS idx_int_log_status_retry
    ON integration_logs (status, retry_count)
    WHERE status = 'RETRYING';

COMMENT ON TABLE  integration_logs                              IS 'ERP entegrasyon deneme kayıtları — Outbox Worker ve Force Retry API tarafından kullanılır';
COMMENT ON COLUMN integration_logs.retry_count                 IS 'Toplam deneme sayısı; 0 = ilk deneme henüz yapılmadı';
COMMENT ON COLUMN integration_logs.outbox_message_id           IS 'İlgili Outbox kaydının UUID''si — FK değil, servis bağımsızlığı korunur';
COMMENT ON COLUMN integration_logs.last_attempt_at             IS 'Son deneme zamanı — Exponential Backoff hesabında kullanılır';

-- ---------------------------------------------------------------------------
-- 5. Seed verisi — temel ERP sistemleri ve iş tipleri
-- ---------------------------------------------------------------------------
INSERT INTO integration_systems (id, code, name, is_active)
VALUES
    (gen_random_uuid(), 'SAP',    'SAP ERP',          TRUE),
    (gen_random_uuid(), 'ORACLE', 'Oracle ERP Cloud',  TRUE),
    (gen_random_uuid(), 'LOGO',   'Logo Tiger / GO',   TRUE),
    (gen_random_uuid(), 'MIKRO',  'Mikro ERP',          TRUE),
    (gen_random_uuid(), 'MOCK',   'Mock (Test)',         TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO integration_jobs (id, code, name, direction, is_active)
VALUES
    (gen_random_uuid(), 'MAT_SYNC',    'Material Card Sync',       'OUTBOUND', TRUE),
    (gen_random_uuid(), 'STOCK_MOVE',  'Inventory Movement Sync',  'OUTBOUND', TRUE),
    (gen_random_uuid(), 'INVOICE_SYNC','Invoice Sync',             'OUTBOUND', TRUE),
    (gen_random_uuid(), 'EX_RATE_PULL','Exchange Rate Pull',       'INBOUND',  TRUE)
ON CONFLICT (code) DO NOTHING;
