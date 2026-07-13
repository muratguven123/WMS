-- V15: Demo kullanıcı ↔ Keycloak sabit UUID eşlemesi
-- Keycloak import: docker/keycloak/import/wms-realm.json
--   kullanıcı id (JWT sub): aaaaaaaa-0000-0000-0000-000000000001
--   şifre: demo

UPDATE users
SET keycloak_user_id = 'aaaaaaaa-0000-0000-0000-000000000001'
WHERE id = '55555555-0000-0000-0000-000000000001'::uuid
  AND (keycloak_user_id IS NULL OR keycloak_user_id = '');
