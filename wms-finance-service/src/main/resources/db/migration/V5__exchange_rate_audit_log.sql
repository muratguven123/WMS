-- =============================================================================
-- V5 — Döviz Kuru Audit Log & Unique Constraint Güncellemesi (Prompt 10.1)
-- =============================================================================

-- ── exchange_rates: rateSource alanını nullable'dan NOT NULL'a çek ───────────
-- Mevcut NULL kayıtları için önce varsayılan değer set et
UPDATE finance.exchange_rates
SET rate_source = 'TCMB'
WHERE rate_source IS NULL;

ALTER TABLE finance.exchange_rates
    ALTER COLUMN rate_source SET NOT NULL;

-- ── exchange_rates: eski unique constraint'i düşür, yenisini ekle ────────────
-- Eski constraint sadece 4 kolon içeriyordu (rate_source yoktu)
ALTER TABLE finance.exchange_rates
    DROP CONSTRAINT IF EXISTS uk_exchange_rate;

ALTER TABLE finance.exchange_rates
    ADD CONSTRAINT uk_exchange_rate
        UNIQUE (source_currency_id, target_currency_id, rate_date, rate_type, rate_source);

-- ── exchange_rates: audit alanlarını ekle ────────────────────────────────────
ALTER TABLE finance.exchange_rates
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Varsayılanı kaldır (yeni kayıtlarda Hibernate @CreationTimestamp yönetir)
ALTER TABLE finance.exchange_rates
    ALTER COLUMN created_at DROP DEFAULT;

-- ── exchange_rate_audit_logs ──────────────────────────────────────────────────
CREATE TABLE finance.exchange_rate_audit_logs (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    exchange_rate_id UUID           NOT NULL,
    action_type      VARCHAR(20)    NOT NULL,
    old_rate         NUMERIC(18, 6),
    new_rate         NUMERIC(18, 6) NOT NULL,
    user_id          UUID,
    changed_at       TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT pk_exchange_rate_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_eral_exchange_rate
        FOREIGN KEY (exchange_rate_id) REFERENCES finance.exchange_rates (id),
    CONSTRAINT chk_eral_action_type
        CHECK (action_type IN ('INSERT', 'UPDATE', 'DELETE'))
);

CREATE INDEX idx_eral_exchange_rate ON finance.exchange_rate_audit_logs (exchange_rate_id);
CREATE INDEX idx_eral_changed_at    ON finance.exchange_rate_audit_logs (changed_at DESC);
CREATE INDEX idx_eral_user_id       ON finance.exchange_rate_audit_logs (user_id)
    WHERE user_id IS NOT NULL;
