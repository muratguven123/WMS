-- =====================================================================
-- V21: Dynamic Table Column Engine (İş İsteri 16)
-- =====================================================================

CREATE TYPE column_data_type AS ENUM (
    'STRING',
    'NUMBER',
    'DATE',
    'BOOLEAN',
    'CUSTOM'
);

CREATE TYPE column_behavior AS ENUM (
    'FORCE_HIDDEN',
    'FORCE_VISIBLE'
);

-- ── table_column_defs ─────────────────────────────────────────────────
CREATE TABLE table_column_defs (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    screen_id        BIGINT       NOT NULL,
    column_key       VARCHAR(100) NOT NULL,
    label_key        VARCHAR(255) NOT NULL,
    data_type        column_data_type NOT NULL DEFAULT 'STRING',
    default_visible  BOOLEAN      NOT NULL DEFAULT TRUE,
    default_sequence INTEGER      NOT NULL DEFAULT 0,
    locked           BOOLEAN      NOT NULL DEFAULT FALSE,
    render_hint      VARCHAR(50),
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,

    CONSTRAINT fk_tcd_screen
        FOREIGN KEY (screen_id) REFERENCES screens (id) ON DELETE RESTRICT,
    CONSTRAINT uk_tcd_screen_column UNIQUE (screen_id, column_key)
);

CREATE INDEX idx_tcd_screen ON table_column_defs (screen_id);
CREATE INDEX idx_tcd_screen_sequence ON table_column_defs (screen_id, default_sequence);

COMMENT ON TABLE table_column_defs IS 'Liste ekranları için kolon tanımları (İş İsteri 16)';

-- ── user_table_preferences ────────────────────────────────────────────
CREATE TABLE user_table_preferences (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    screen_id   BIGINT      NOT NULL,
    preferences JSONB       NOT NULL DEFAULT '[]'::jsonb,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_utp_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_utp_screen
        FOREIGN KEY (screen_id) REFERENCES screens (id) ON DELETE CASCADE,
    CONSTRAINT uk_utp_user_screen UNIQUE (user_id, screen_id)
);

CREATE INDEX idx_utp_user ON user_table_preferences (user_id);

COMMENT ON TABLE user_table_preferences IS 'Kullanıcı bazlı tablo kolon tercihleri (tek satır JSONB)';

-- ── column_behavior_rules ─────────────────────────────────────────────
CREATE TABLE column_behavior_rules (
    id                  BIGINT           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_column_def_id BIGINT           NOT NULL,
    priority            INTEGER          NOT NULL,
    role_id             BIGINT,
    company_id          BIGINT,
    behavior            column_behavior  NOT NULL,
    updated_at          TIMESTAMPTZ,

    CONSTRAINT fk_cbr_column
        FOREIGN KEY (table_column_def_id) REFERENCES table_column_defs (id) ON DELETE CASCADE
);

CREATE INDEX idx_cbr_column ON column_behavior_rules (table_column_def_id);
CREATE INDEX idx_cbr_role ON column_behavior_rules (role_id);
CREATE INDEX idx_cbr_company ON column_behavior_rules (company_id);

COMMENT ON TABLE column_behavior_rules IS 'Rol/şirket bazlı kolon görünürlük kuralları';
