-- =====================================================================
-- V4: Approval Request Schema
-- İş İsteri 3 — Onay mekanizması (requiresApproval = true)
-- Flyway migration
-- =====================================================================

CREATE TYPE approval_status AS ENUM (
    'PENDING_APPROVAL',
    'APPROVED',
    'REJECTED'
);

CREATE TABLE approval_requests (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    step_config_id        UUID            NOT NULL,           -- LocationProcessStepConfig.id
    reference_type        VARCHAR(50)     NOT NULL,           -- ORDER, TRANSFER, RECEIPT vb.
    reference_id          UUID            NOT NULL,           -- ilgili nesne UUID'si
    requested_by_user_id  UUID            NOT NULL,
    approved_by_user_id   UUID,                               -- null: henüz işleme alınmamış
    status                approval_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    reviewer_note         VARCHAR(500),
    approved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ
);

CREATE INDEX idx_approval_status      ON approval_requests (status);
CREATE INDEX idx_approval_requested_by ON approval_requests (requested_by_user_id);
CREATE INDEX idx_approval_step_config ON approval_requests (step_config_id);
CREATE INDEX idx_approval_reference   ON approval_requests (reference_type, reference_id);
