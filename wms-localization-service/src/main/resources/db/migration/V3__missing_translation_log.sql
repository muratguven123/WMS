-- =============================================================================
-- Flyway Migration: V3__missing_translation_log.sql
-- Eksik çeviri denetim tablosu
-- =============================================================================

CREATE TABLE missing_translation_log
(
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    locale        VARCHAR(10)  NOT NULL,
    key_code      VARCHAR(255) NOT NULL,
    module        VARCHAR(50),
    first_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    hit_count     BIGINT       NOT NULL DEFAULT 1,
    resolved      BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_at   TIMESTAMPTZ,

    CONSTRAINT pk_missing_translation_log PRIMARY KEY (id),
    CONSTRAINT uq_mtl_locale_key UNIQUE (locale, key_code),
    CONSTRAINT chk_mtl_hit_count CHECK (hit_count >= 1)
);

CREATE INDEX idx_mtl_key_code  ON missing_translation_log (key_code);
CREATE INDEX idx_mtl_locale    ON missing_translation_log (locale);
CREATE INDEX idx_mtl_resolved  ON missing_translation_log (resolved);
CREATE INDEX idx_mtl_last_seen ON missing_translation_log (last_seen_at DESC);

-- Admin dashboard için: çözümlenmemiş + en çok hit alan üstte
CREATE INDEX idx_mtl_unresolved_hits
    ON missing_translation_log (resolved, hit_count DESC)
    WHERE resolved = FALSE;

COMMENT ON TABLE  missing_translation_log           IS 'Çevirisi bulunamayan anahtar kayıtları';
COMMENT ON COLUMN missing_translation_log.hit_count IS 'Kaç kez eksik bulundu — önceliklendirme için';
COMMENT ON COLUMN missing_translation_log.resolved  IS 'Çeviri tamamlandıktan sonra admin tarafından işaretlenir';
