-- =============================================================================
-- WMS Integration Service — Faz 4.7 DDL
-- İş isteri 7.3: yeni bağlantı yöntemleri (MESSAGE_QUEUE, WEBHOOK) ve
-- dosya formatı çeşitliliği (CSV/XML/JSON/EXCEL).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. connection_type genişletmesi
--    'MESSAGE_QUEUE' 13 karakter olduğundan kolon VARCHAR(20)'ye çıkarılır;
--    CHECK kısıtı yeni değerlerle yeniden tanımlanır.
-- ---------------------------------------------------------------------------
ALTER TABLE location_integration_configs
    ALTER COLUMN connection_type TYPE VARCHAR(20);

ALTER TABLE location_integration_configs
    DROP CONSTRAINT IF EXISTS chk_connection_type;

ALTER TABLE location_integration_configs
    ADD CONSTRAINT chk_connection_type
        CHECK (connection_type IN ('REST','SOAP','SFTP','DB','MESSAGE_QUEUE','WEBHOOK'));

COMMENT ON COLUMN location_integration_configs.connection_type
    IS 'REST | SOAP | SFTP | DB | MESSAGE_QUEUE | WEBHOOK';

-- ---------------------------------------------------------------------------
-- 2. file_format kolonu
--    SFTP / dosya bazlı senaryolarda üretilecek dosya biçimi.
--    Diğer bağlantı tiplerinde NULL bırakılır.
-- ---------------------------------------------------------------------------
ALTER TABLE location_integration_configs
    ADD COLUMN IF NOT EXISTS file_format VARCHAR(10);

ALTER TABLE location_integration_configs
    ADD CONSTRAINT chk_file_format
        CHECK (file_format IS NULL OR file_format IN ('CSV','XML','JSON','EXCEL'));

COMMENT ON COLUMN location_integration_configs.file_format
    IS 'Dosya bazlı entegrasyonlarda (SFTP) dosya biçimi: CSV | XML | JSON | EXCEL; diğer tiplerde NULL';

-- ---------------------------------------------------------------------------
-- 3. integration_systems — webhook alanları
--    WEBHOOK bağlantı tipinde Outbox Worker'ın POST edeceği hedef URL ve
--    HMAC-SHA256 imza anahtarı.
-- ---------------------------------------------------------------------------
ALTER TABLE integration_systems
    ADD COLUMN IF NOT EXISTS webhook_url    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(200);

COMMENT ON COLUMN integration_systems.webhook_url
    IS 'WEBHOOK bağlantı tipinde hedef callback URL (yalnızca HTTPS önerilir)';
COMMENT ON COLUMN integration_systems.webhook_secret
    IS 'Webhook HMAC-SHA256 imza anahtarı — üretimde Vault/Secrets Manager referansı saklanmalı';
