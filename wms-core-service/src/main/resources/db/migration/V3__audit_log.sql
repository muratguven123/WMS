-- =====================================================================
-- V3: Configuration Audit Log Schema
-- İş İsteri 3 — Konfigürasyon değişikliklerinin denetim kaydı
-- Flyway migration
-- =====================================================================

CREATE TABLE configuration_audit_logs (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_name          VARCHAR(100) NOT NULL,
    entity_id            UUID         NOT NULL,
    action_type          VARCHAR(20)  NOT NULL,      -- UPDATE | ACTIVATE | DEACTIVATE
    changed_field_name   VARCHAR(100) NOT NULL,
    old_value            VARCHAR(500),
    new_value            VARCHAR(500),
    changed_by_user_id   UUID,                       -- null: sistem tetiklemesi
    changed_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Sık sorgulanan eksenlere index
CREATE INDEX idx_audit_entity     ON configuration_audit_logs (entity_name, entity_id);
CREATE INDEX idx_audit_changed_by ON configuration_audit_logs (changed_by_user_id);
CREATE INDEX idx_audit_changed_at ON configuration_audit_logs (changed_at DESC);

-- Log kayıtları hiçbir zaman güncellenmez/silinmez:
-- Row Level Security ile korunabilir (opsiyonel, production'da önerilir).
-- REVOKE UPDATE, DELETE ON configuration_audit_logs FROM wms_app_user;
