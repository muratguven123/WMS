-- =============================================================================
-- V13: UI demo çeviri anahtarları + ülke format konfigürasyonları
--
-- İçerik:
--   - Diller: tr / en (V2'de mevcut — idempotent güvence)
--   - Çeviri anahtarları: button.save, label.username, error.unauthorized
--   - Ülke format konfigürasyonları: TR, DE, US
--     (country_id değerleri wms-core-service'in GERÇEK demo UUID'leriyle hizalı:
--      TR=aaaaaaaa-…001, US=aaaaaaaa-…010 [core V9], DE=aaaaaaaa-…020 [core V14])
--
-- NOT: V6'daki cccccccc-…001 / dddddddd-…001 country_id'li format kayıtları
--      core'daki hiçbir ülkeye karşılık gelmeyen legacy demo verisidir;
--      UNIQUE(country_id) kısıtı nedeniyle bu migration'la çakışmazlar.
-- Tüm insert'ler idempotent'tir.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Diller (V2 seed'i ile aynı — yeniden çalıştırılabilirlik güvencesi)
-- ---------------------------------------------------------------------------
INSERT INTO language (code, name, is_default, is_active) VALUES
    ('tr', 'Türkçe',  TRUE,  TRUE),
    ('en', 'English', FALSE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Çeviri anahtarları (module: UI)
-- ---------------------------------------------------------------------------
INSERT INTO translation_key (key_code, module, description) VALUES
    ('button.save',        'UI', 'Kaydet butonu etiketi'),
    ('label.username',     'UI', 'Kullanıcı adı alan etiketi'),
    ('error.unauthorized', 'UI', 'Yetkisiz işlem hata mesajı (401/403)')
ON CONFLICT (key_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Çeviri değerleri (dil + anahtar kombinasyonu benzersizdir)
-- ---------------------------------------------------------------------------
INSERT INTO translation_value (language_id, translation_key_id, value)
SELECT l.id, tk.id, v.value
FROM (VALUES
    ('tr', 'button.save',        'Kaydet'),
    ('en', 'button.save',        'Save'),
    ('tr', 'label.username',     'Kullanıcı Adı'),
    ('en', 'label.username',     'Username'),
    ('tr', 'error.unauthorized', 'Bu işlem için yetkiniz bulunmamaktadır.'),
    ('en', 'error.unauthorized', 'You are not authorized to perform this operation.')
) AS v(lang_code, key_code, value)
JOIN language        l  ON l.code      = v.lang_code
JOIN translation_key tk ON tk.key_code = v.key_code
ON CONFLICT (language_id, translation_key_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Ülke bazlı tarih/saat/sayı format konfigürasyonları
--    TR: 06.07.2026 14:30 | 1.234,56
--    DE: 06.07.2026 14:30 | 1.234,56
--    US: 07/06/2026 02:30 PM | 1,234.56
-- ---------------------------------------------------------------------------
INSERT INTO country_format_config (
    country_id, date_format, time_format, decimal_separator, thousand_separator
) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001'::uuid, 'dd.MM.yyyy', 'HH:mm',   ',', '.'),  -- TR
    ('aaaaaaaa-0000-0000-0000-000000000020'::uuid, 'dd.MM.yyyy', 'HH:mm',   ',', '.'),  -- DE
    ('aaaaaaaa-0000-0000-0000-000000000010'::uuid, 'MM/dd/yyyy', 'hh:mm a', '.', ',')   -- US
ON CONFLICT (country_id) DO NOTHING;
