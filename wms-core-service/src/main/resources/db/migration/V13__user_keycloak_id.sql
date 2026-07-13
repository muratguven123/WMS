-- Keycloak entegrasyonu: JWT sub claim ile yerel kullanıcı eşlemesi
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS keycloak_user_id VARCHAR(36);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS uk_user_keycloak_id;

ALTER TABLE users
    ADD CONSTRAINT uk_user_keycloak_id UNIQUE (keycloak_user_id);

COMMENT ON COLUMN users.keycloak_user_id IS
    'Keycloak kullanıcı kimliği (JWT sub). Nullable — eski kayıtlar için.';
