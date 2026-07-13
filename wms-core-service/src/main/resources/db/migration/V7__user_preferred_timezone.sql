-- Prompt 6.1: Kullanıcı profil timezone tercihi
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_timezone VARCHAR(50);

COMMENT ON COLUMN users.preferred_timezone IS
    'IANA timezone tercihi (örn: Europe/Istanbul). Null ise Location.timezone kullanılır.';
