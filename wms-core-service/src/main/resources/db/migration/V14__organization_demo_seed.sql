-- =============================================================================
-- V14: Demo organizasyon seed verisi — UI entegrasyonu ve Faz 2 Dinamik UI testi
--
-- İçerik:
--   1 Holding, 2 Şirket (TR/DE), DE ülkesi + Berlin, 2 Depo, 3 Rol,
--   1 Demo kullanıcı + UserAccess matrisi, REC_CONTROL_FORM alan kuralları.
--
-- UUID konvansiyonu (mevcut demo seed'lerle uyumlu):
--   aaaaaaaa-* : adres master (V9'dan devam — TR=…001, US=…010, DE=…020)
--   bbbbbbbb-* : lokasyonlar (V6/V11/finance V2 zaten …001'i referans alıyor)
--   11111111/22222222/33333333/44444444/55555555/66666666 : org/şirket/bölge/rol/kullanıcı/erişim
--
-- NOT: Rol adları @PreAuthorize kontratıyla birebir aynıdır:
--      WMS_ADMIN, WAREHOUSE_MANAGER, PICKER (bkz. controller'lar).
-- Tüm insert'ler idempotent'tir (WHERE NOT EXISTS / ON CONFLICT).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. Önkoşul seed — Flyway baseline (v12) V9 adres seed'ini atladığı için
--    TR ülkesi + İstanbul burada garanti edilir.
-- ---------------------------------------------------------------------------
INSERT INTO countries (id, iso_code, name, is_active, created_at)
SELECT 'aaaaaaaa-0000-0000-0000-000000000001'::uuid, 'TR', 'Türkiye', TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM countries WHERE iso_code = 'TR');

INSERT INTO cities (id, country_id, state_province_id, name, is_active, created_at)
SELECT 'aaaaaaaa-0000-0000-0000-000000000002'::uuid, c.id, NULL, 'İstanbul', TRUE, now()
FROM countries c
WHERE c.iso_code = 'TR'
  AND NOT EXISTS (
      SELECT 1 FROM cities ci
      WHERE ci.country_id = c.id AND ci.name = 'İstanbul'
  );

-- ---------------------------------------------------------------------------
-- 1. Ülke: Almanya (TR, yukarıda garanti edildi)
-- ---------------------------------------------------------------------------
INSERT INTO countries (id, iso_code, name, is_active, created_at)
SELECT 'aaaaaaaa-0000-0000-0000-000000000020'::uuid, 'DE', 'Deutschland', TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM countries WHERE iso_code = 'DE');

-- Berlin (DE — eyaletsiz akış; şehir-eyalet ilişkisi zorunlu değil)
INSERT INTO cities (id, country_id, state_province_id, name, is_active, created_at)
SELECT 'aaaaaaaa-0000-0000-0000-000000000021'::uuid, c.id, NULL, 'Berlin', TRUE, now()
FROM countries c
WHERE c.iso_code = 'DE'
  AND NOT EXISTS (
      SELECT 1 FROM cities ci WHERE ci.country_id = c.id AND ci.name = 'Berlin'
  );

-- Tuzla ilçesi (İstanbul V9'da mevcut — depo adresi için)
INSERT INTO districts (id, city_id, name, is_active, created_at)
SELECT 'aaaaaaaa-0000-0000-0000-000000000005'::uuid, ci.id, 'Tuzla', TRUE, now()
FROM cities ci
JOIN countries c ON c.id = ci.country_id
WHERE c.iso_code = 'TR' AND ci.name = 'İstanbul'
  AND NOT EXISTS (
      SELECT 1 FROM districts d WHERE d.city_id = ci.id AND d.name = 'Tuzla'
  );

-- ---------------------------------------------------------------------------
-- 2. Holding / Organizasyon
-- ---------------------------------------------------------------------------
INSERT INTO organizations (id, name, is_active, created_at)
SELECT '11111111-0000-0000-0000-000000000001'::uuid, 'Global WMS Holding', TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM organizations WHERE id = '11111111-0000-0000-0000-000000000001'
);

-- ---------------------------------------------------------------------------
-- 3. Şirketler
-- ---------------------------------------------------------------------------
INSERT INTO companies (id, organization_id, name, tax_number, tax_office, is_active, created_at)
SELECT '22222222-0000-0000-0000-000000000001'::uuid,
       '11111111-0000-0000-0000-000000000001'::uuid,
       'Logistics Corp TR', '1234567890', 'Tuzla Vergi Dairesi', TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM companies WHERE tax_number = '1234567890');

INSERT INTO companies (id, organization_id, name, tax_number, tax_office, is_active, created_at)
SELECT '22222222-0000-0000-0000-000000000002'::uuid,
       '11111111-0000-0000-0000-000000000001'::uuid,
       'Logistics Corp DE', 'DE812345678', 'Finanzamt Berlin Mitte', TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM companies WHERE tax_number = 'DE812345678');

-- ---------------------------------------------------------------------------
-- 4. Bölgeler (locations.region_id NOT NULL olduğu için zorunlu)
-- ---------------------------------------------------------------------------
INSERT INTO regions (id, country_id, name, is_active, created_at)
SELECT '33333333-0000-0000-0000-000000000001'::uuid, c.id, 'Marmara', TRUE, now()
FROM countries c
WHERE c.iso_code = 'TR'
  AND NOT EXISTS (
      SELECT 1 FROM regions r WHERE r.country_id = c.id AND r.name = 'Marmara'
  );

INSERT INTO regions (id, country_id, name, is_active, created_at)
SELECT '33333333-0000-0000-0000-000000000002'::uuid, c.id, 'Berlin-Brandenburg', TRUE, now()
FROM countries c
WHERE c.iso_code = 'DE'
  AND NOT EXISTS (
      SELECT 1 FROM regions r WHERE r.country_id = c.id AND r.name = 'Berlin-Brandenburg'
  );

-- ---------------------------------------------------------------------------
-- 5. Lokasyonlar / Depolar
--    DİKKAT: bbbbbbbb-…001 UUID'si V6 (süreç konfigi), V11 (dinamik UI kuralı)
--    ve finance V2 (para birimi ayarı) tarafından zaten referans alınıyor.
--    Bu insert, o "askıdaki" referansları gerçek bir depoya bağlar.
-- ---------------------------------------------------------------------------
INSERT INTO locations (id, company_id, region_id, name, type, timezone, is_active, created_at)
SELECT 'bbbbbbbb-0000-0000-0000-000000000001'::uuid,
       '22222222-0000-0000-0000-000000000001'::uuid,
       '33333333-0000-0000-0000-000000000001'::uuid,
       'İstanbul Tuzla Deposu', 'CENTRAL', 'Europe/Istanbul', TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM locations WHERE id = 'bbbbbbbb-0000-0000-0000-000000000001'
);

INSERT INTO locations (id, company_id, region_id, name, type, timezone, is_active, created_at)
SELECT 'bbbbbbbb-0000-0000-0000-000000000002'::uuid,
       '22222222-0000-0000-0000-000000000002'::uuid,
       '33333333-0000-0000-0000-000000000002'::uuid,
       'Berlin Central Deposu', 'DISTRIBUTION', 'Europe/Berlin', TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM locations WHERE id = 'bbbbbbbb-0000-0000-0000-000000000002'
);

-- ---------------------------------------------------------------------------
-- 6. Sistem rolleri — adlar @PreAuthorize ifadeleriyle birebir eşleşir
-- ---------------------------------------------------------------------------
INSERT INTO roles (id, name, permissions, is_active, created_at)
SELECT '44444444-0000-0000-0000-000000000001'::uuid, 'WMS_ADMIN',
       '["*"]'::jsonb, TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'WMS_ADMIN');

INSERT INTO roles (id, name, permissions, is_active, created_at)
SELECT '44444444-0000-0000-0000-000000000002'::uuid, 'WAREHOUSE_MANAGER',
       '["inbound.manage", "stock.manage", "approval.decide", "report.view"]'::jsonb, TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'WAREHOUSE_MANAGER');

INSERT INTO roles (id, name, permissions, is_active, created_at)
SELECT '44444444-0000-0000-0000-000000000003'::uuid, 'PICKER',
       '["picking.execute", "stock.view"]'::jsonb, TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PICKER');

-- ---------------------------------------------------------------------------
-- 7. Demo kullanıcı
--    Kimlik doğrulama Keycloak'tadır: password_hash placeholder'dır,
--    keycloak_user_id Keycloak'ta kullanıcı oluşturulduktan sonra
--    JWT sub değeriyle güncellenmelidir (bkz. V13 migration notu).
-- ---------------------------------------------------------------------------
INSERT INTO users (id, username, email, password_hash, is_active, created_at)
SELECT '55555555-0000-0000-0000-000000000001'::uuid,
       'demo.user', 'demo.user@wms.local', 'KEYCLOAK_MANAGED', TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'demo.user');

-- ---------------------------------------------------------------------------
-- 8. UserAccess matrisi
--    WMS_ADMIN         → TR şirketi, TÜM lokasyonlar (location_id NULL)
--    WAREHOUSE_MANAGER → TR şirketi, yalnız Tuzla
--    PICKER            → DE şirketi, yalnız Berlin
-- ---------------------------------------------------------------------------
INSERT INTO user_accesses (id, user_id, company_id, location_id, role_id)
SELECT '66666666-0000-0000-0000-000000000001'::uuid,
       '55555555-0000-0000-0000-000000000001'::uuid,
       '22222222-0000-0000-0000-000000000001'::uuid,
       NULL,
       '44444444-0000-0000-0000-000000000001'::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM user_accesses WHERE id = '66666666-0000-0000-0000-000000000001'
);

INSERT INTO user_accesses (id, user_id, company_id, location_id, role_id)
SELECT '66666666-0000-0000-0000-000000000002'::uuid,
       '55555555-0000-0000-0000-000000000001'::uuid,
       '22222222-0000-0000-0000-000000000001'::uuid,
       'bbbbbbbb-0000-0000-0000-000000000001'::uuid,
       '44444444-0000-0000-0000-000000000002'::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM user_accesses WHERE id = '66666666-0000-0000-0000-000000000002'
);

INSERT INTO user_accesses (id, user_id, company_id, location_id, role_id)
SELECT '66666666-0000-0000-0000-000000000003'::uuid,
       '55555555-0000-0000-0000-000000000001'::uuid,
       '22222222-0000-0000-0000-000000000002'::uuid,
       'bbbbbbbb-0000-0000-0000-000000000002'::uuid,
       '44444444-0000-0000-0000-000000000003'::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM user_accesses WHERE id = '66666666-0000-0000-0000-000000000003'
);

-- ---------------------------------------------------------------------------
-- 9. Dinamik UI — REC_CONTROL_FORM ekranı + alanları (V10 baseline atlandı)
-- ---------------------------------------------------------------------------
INSERT INTO screens (id, code, name, is_active, created_at)
SELECT 'a1000000-0000-0000-0000-000000000001'::uuid, 'REC_CONTROL_FORM', 'Mal Kabul Kontrol Formu', TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM screens WHERE code = 'REC_CONTROL_FORM');

INSERT INTO screens (id, code, name, is_active, created_at)
SELECT 'a1000000-0000-0000-0000-000000000002'::uuid, 'MAT_CARD_FORM', 'Malzeme Kartı Formu', TRUE, now()
WHERE NOT EXISTS (SELECT 1 FROM screens WHERE code = 'MAT_CARD_FORM');

INSERT INTO screen_fields (id, screen_id, field_key, default_behavior, data_type, is_active, created_at)
SELECT 'b1000000-0000-0000-0000-000000000001'::uuid,
       'a1000000-0000-0000-0000-000000000001'::uuid, 'tax_number', 'OPTIONAL', 'STRING', TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM screen_fields WHERE screen_id = 'a1000000-0000-0000-0000-000000000001'::uuid
      AND field_key = 'tax_number'
);

INSERT INTO screen_fields (id, screen_id, field_key, default_behavior, data_type, is_active, created_at)
SELECT 'b1000000-0000-0000-0000-000000000002'::uuid,
       'a1000000-0000-0000-0000-000000000001'::uuid, 'district', 'OPTIONAL', 'STRING', TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM screen_fields WHERE screen_id = 'a1000000-0000-0000-0000-000000000001'::uuid
      AND field_key = 'district'
);

INSERT INTO screen_fields (id, screen_id, field_key, default_behavior, data_type, is_active, created_at)
SELECT 'b1000000-0000-0000-0000-000000000003'::uuid,
       'a1000000-0000-0000-0000-000000000001'::uuid, 'zip_code', 'OPTIONAL', 'STRING', TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM screen_fields WHERE screen_id = 'a1000000-0000-0000-0000-000000000001'::uuid
      AND field_key = 'zip_code'
);

INSERT INTO screen_fields (id, screen_id, field_key, default_behavior, data_type, is_active, created_at)
SELECT 'b1000000-0000-0000-0000-000000000004'::uuid,
       'a1000000-0000-0000-0000-000000000001'::uuid, 'batch_no', 'HIDDEN', 'STRING', TRUE, now()
WHERE NOT EXISTS (
    SELECT 1 FROM screen_fields WHERE screen_id = 'a1000000-0000-0000-0000-000000000001'::uuid
      AND field_key = 'batch_no'
);

-- ---------------------------------------------------------------------------
-- 10. Dinamik UI — REC_CONTROL_FORM ülke/rol bazlı alan kuralları (Faz 2)
--    Ekran ve alanlar V10'da mevcut:
--      screen  a1000000-…001 REC_CONTROL_FORM
--      fields  b1000000-…001 tax_number | …002 district | …003 zip_code | …004 batch_no
--
--    V10'daki placeholder kural c1000000-…002 (district HIDDEN @ Tuzla, priority 50)
--    siliniyor: Tuzla artık TR'nin tek deposu olduğundan, aşağıdaki
--    "TR'de district MANDATORY" ülke kuralını (priority 20) ezerek
--    demo senaryosunu görünmez kılıyordu.
-- ---------------------------------------------------------------------------
DELETE FROM field_behavior_rules
WHERE id = 'c1000000-0000-0000-0000-000000000002';

-- Kural 1: Türkiye'de "district" (ilçe) → MANDATORY
INSERT INTO field_behavior_rules
    (id, screen_field_id, priority, country_id, behavior, validation_error_message_key)
SELECT 'c1000000-0000-0000-0000-000000000003'::uuid,
       'b1000000-0000-0000-0000-000000000002'::uuid,
       20,
       (SELECT id FROM countries WHERE iso_code = 'TR'),
       'MANDATORY',
       'validation.district.required'
WHERE NOT EXISTS (
    SELECT 1 FROM field_behavior_rules WHERE id = 'c1000000-0000-0000-0000-000000000003'
);

-- Kural 2: Almanya'da "district" (ilçe) → HIDDEN
INSERT INTO field_behavior_rules
    (id, screen_field_id, priority, country_id, behavior)
SELECT 'c1000000-0000-0000-0000-000000000004'::uuid,
       'b1000000-0000-0000-0000-000000000002'::uuid,
       20,
       (SELECT id FROM countries WHERE iso_code = 'DE'),
       'HIDDEN'
WHERE NOT EXISTS (
    SELECT 1 FROM field_behavior_rules WHERE id = 'c1000000-0000-0000-0000-000000000004'
);

-- Kural 3: Almanya'da "zip_code" → MANDATORY + 5 haneli PLZ regex
INSERT INTO field_behavior_rules
    (id, screen_field_id, priority, country_id, behavior,
     validation_regex, validation_error_message_key)
SELECT 'c1000000-0000-0000-0000-000000000005'::uuid,
       'b1000000-0000-0000-0000-000000000003'::uuid,
       20,
       (SELECT id FROM countries WHERE iso_code = 'DE'),
       'MANDATORY',
       '^\d{5}$',
       'validation.zip_code.invalid'
WHERE NOT EXISTS (
    SELECT 1 FROM field_behavior_rules WHERE id = 'c1000000-0000-0000-0000-000000000005'
);

-- Kural 4: WAREHOUSE_MANAGER rolü için "batch_no" → MANDATORY
--          (varsayılanı HIDDEN; rol kuralı priority 40 ile ülke kurallarını ezer)
INSERT INTO field_behavior_rules
    (id, screen_field_id, priority, role_id, behavior)
SELECT 'c1000000-0000-0000-0000-000000000006'::uuid,
       'b1000000-0000-0000-0000-000000000004'::uuid,
       40,
       '44444444-0000-0000-0000-000000000002'::uuid,
       'MANDATORY'
WHERE NOT EXISTS (
    SELECT 1 FROM field_behavior_rules WHERE id = 'c1000000-0000-0000-0000-000000000006'
);
