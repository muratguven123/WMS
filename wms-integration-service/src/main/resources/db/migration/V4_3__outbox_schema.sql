-- =============================================================================
-- WMS Integration Service — Faz 4.3 DDL
-- Outbox Pattern mesaj tablosu
-- =============================================================================

-- ---------------------------------------------------------------------------
-- outbox_messages
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_messages (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(100) NOT NULL,
    aggregate_id        UUID        NOT NULL,
    job_code            VARCHAR(50) NOT NULL,
    payload             TEXT        NOT NULL,
    location_id         UUID        NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count         INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at     TIMESTAMPTZ,
    external_reference  VARCHAR(200),
    error_message       TEXT,
    -- BaseEntity alanları
    is_active           BOOLEAN,               -- Outbox'ta anlamsız
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,           -- Outbox'ta kullanılmaz

    CONSTRAINT pk_outbox_messages PRIMARY KEY (id),
    CONSTRAINT chk_outbox_status CHECK (
        status IN ('PENDING','PROCESSING','COMPLETED','FAILED','FAILED_MAX_RETRIES')
    ),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0)
);

-- Worker'ın birincil sorgusunu destekler:
--   WHERE status IN ('PENDING','FAILED') AND next_attempt_at <= NOW()
CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt
    ON outbox_messages (status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

-- Aggregate bazlı idempotency kontrolü
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_messages (aggregate_type, aggregate_id);

-- 30 günlük temizleme job'ı sorgusunu hızlandırır
CREATE INDEX IF NOT EXISTS idx_outbox_created_at
    ON outbox_messages (created_at DESC);

COMMENT ON TABLE  outbox_messages                 IS 'Transactional Outbox Pattern mesaj tablosu — Outbox Worker bu tablodan okur';
COMMENT ON COLUMN outbox_messages.aggregate_type  IS 'Domain nesne tipi: InventoryMovement, Invoice, MaterialCard';
COMMENT ON COLUMN outbox_messages.job_code        IS 'IntegrationJob.code ile örtüşür: STOCK_MOVE, INVOICE_SYNC, MAT_SYNC';
COMMENT ON COLUMN outbox_messages.next_attempt_at IS 'Worker bu zamandan önce bu kaydı işlemez (Exponential Backoff)';
COMMENT ON COLUMN outbox_messages.retry_count     IS '0 = henüz denenmedi; max = outbox.retry.max-attempts config değeri';
