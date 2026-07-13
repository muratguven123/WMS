-- =============================================================================
-- Flyway Migration: V2__outbox_messages.sql
-- Description: Creates outbox_messages table for Transactional Outbox pattern
-- =============================================================================

CREATE TABLE outbox_messages
(
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    processed_at   TIMESTAMP,
    error_message  TEXT,

    CONSTRAINT pk_outbox_messages PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_messages_status ON outbox_messages (status);
CREATE INDEX idx_outbox_messages_aggregate ON outbox_messages (aggregate_type, aggregate_id);

COMMENT ON TABLE  outbox_messages                IS 'Transactional Outbox deseni için mesaj tablosu';
COMMENT ON COLUMN outbox_messages.id             IS 'Benzersiz outbox mesaj UUID';
COMMENT ON COLUMN outbox_messages.aggregate_type IS 'Domain nesnesinin tipi (örn: OutboundOrder)';
COMMENT ON COLUMN outbox_messages.aggregate_id   IS 'Tetikleyici nesnenin UUID referansı';
COMMENT ON COLUMN outbox_messages.payload        IS 'Giden JSON verisi';
COMMENT ON COLUMN outbox_messages.status         IS 'Mesaj gönderim durumu: PENDING, PROCESSED, FAILED';
COMMENT ON COLUMN outbox_messages.created_at     IS 'Mesaj oluşturulma zamanı';
COMMENT ON COLUMN outbox_messages.processed_at   IS 'Mesajın başarıyla iletildiği zaman';
COMMENT ON COLUMN outbox_messages.error_message  IS 'Gönderim sırasında hata oluşursa hata detayları';
