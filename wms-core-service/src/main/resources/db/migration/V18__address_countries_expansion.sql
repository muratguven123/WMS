-- =============================================================================
-- V18 — 15 dil/ülke desteği için yeni ülke + minimal cascade demo verisi
--
-- WMS_Dinamik_Adres_Plani_v3/v4'te onaylanan ülke matrisine göre eklenir.
-- TR (V9), US (V9), DE (V14) zaten mevcut; burada 12 yeni ülke eklenir:
-- ES, FR, IT, PT, RU, SA, CN, JP, KR, IN, NL, PL.
--
-- Her ülke için sadece cascade akışını test edebilecek minimal demo veri
-- eklenir (1 state/province + 1 city, state'i olmayan ülkeler için sadece
-- 1 city; district seviyesi kullanan ülkeler için 1 district). Kapsamlı
-- gerçek il/ilçe listesi ayrı bir veri yükleme işidir (bkz. plan §2.3).
--
-- Tüm insert'ler idempotent'tir (WHERE NOT EXISTS), id'ler auto IDENTITY
-- ile üretilir — localization-service'in V16 seed'i bu id'leri dblink
-- üzerinden iso_code'a göre çözer, literal id varsayımı yapılmaz.
--
-- created_at: V16 uuid_to_bigint sonrası DEFAULT kalktığı için explicit now().
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Ülkeler
-- ---------------------------------------------------------------------------
INSERT INTO countries (iso_code, name, is_active, created_at)
SELECT v.iso_code, v.name, TRUE, now()
FROM (VALUES
    ('ES', 'España'),
    ('IT', 'Italia'),
    ('RU', 'Россия'),
    ('SA', 'Saudi Arabia'),
    ('CN', '中国'),
    ('JP', '日本'),
    ('KR', '대한민국'),
    ('IN', 'India'),
    ('FR', 'France'),
    ('PT', 'Portugal'),
    ('NL', 'Nederland'),
    ('PL', 'Polska')
) AS v(iso_code, name)
WHERE NOT EXISTS (SELECT 1 FROM countries c WHERE c.iso_code = v.iso_code);

-- ---------------------------------------------------------------------------
-- State/Province gerektiren ülkeler: ES, IT, RU, SA, CN, JP, KR, IN
-- (1'er demo state/province)
-- ---------------------------------------------------------------------------
INSERT INTO state_provinces (country_id, name, code, is_active, created_at)
SELECT c.id, v.state_name, v.state_code, TRUE, now()
FROM countries c
JOIN (VALUES
    ('ES', 'Madrid',            'M'),
    ('IT', 'Lazio',             'RM'),
    ('RU', 'Moskovskaya oblast', 'MOW'),
    ('SA', 'Riyadh Region',     'RY'),
    ('CN', 'Guangdong',         'GD'),
    ('JP', 'Tōkyō-to',          'TK'),
    ('KR', 'Seoul-teukbyeolsi', 'SE'),
    ('IN', 'Maharashtra',       'MH')
) AS v(iso_code, state_name, state_code) ON v.iso_code = c.iso_code
WHERE NOT EXISTS (
    SELECT 1 FROM state_provinces sp
    WHERE sp.country_id = c.id AND sp.code = v.state_code
);

-- ---------------------------------------------------------------------------
-- Şehirler — state'li ülkeler (state_province_id dolu)
-- ---------------------------------------------------------------------------
INSERT INTO cities (country_id, state_province_id, name, is_active, created_at)
SELECT c.id, sp.id, v.city_name, TRUE, now()
FROM countries c
JOIN state_provinces sp ON sp.country_id = c.id
JOIN (VALUES
    ('ES', 'M',   'Madrid'),
    ('IT', 'RM',  'Roma'),
    ('RU', 'MOW', 'Moskva'),
    ('SA', 'RY',  'Riyadh'),
    ('CN', 'GD',  'Guangzhou'),
    ('JP', 'TK',  'Shibuya'),
    ('KR', 'SE',  'Gangnam-gu'),
    ('IN', 'MH',  'Mumbai')
) AS v(iso_code, state_code, city_name) ON v.iso_code = c.iso_code AND v.state_code = sp.code
WHERE NOT EXISTS (
    SELECT 1 FROM cities ci WHERE ci.state_province_id = sp.id AND ci.name = v.city_name
);

-- ---------------------------------------------------------------------------
-- Şehirler — state'i olmayan ülkeler (state_province_id NULL)
-- ---------------------------------------------------------------------------
INSERT INTO cities (country_id, state_province_id, name, is_active, created_at)
SELECT c.id, NULL, v.city_name, TRUE, now()
FROM countries c
JOIN (VALUES
    ('FR', 'Paris'),
    ('PT', 'Lisboa'),
    ('NL', 'Amsterdam'),
    ('PL', 'Warszawa')
) AS v(iso_code, city_name) ON v.iso_code = c.iso_code
WHERE NOT EXISTS (
    SELECT 1 FROM cities ci WHERE ci.country_id = c.id AND ci.name = v.city_name
);

-- ---------------------------------------------------------------------------
-- District seviyesi kullanan ülkeler (matris: SA, CN, KR — TR zaten V9'da mevcut)
-- ---------------------------------------------------------------------------
INSERT INTO districts (city_id, name, is_active, created_at)
SELECT ci.id, v.district_name, TRUE, now()
FROM cities ci
JOIN countries c ON c.id = ci.country_id
JOIN (VALUES
    ('SA', 'Riyadh',      'Al Olaya'),
    ('CN', 'Guangzhou',   'Tianhe'),
    ('KR', 'Gangnam-gu',  'Yeoksam-dong')
) AS v(iso_code, city_name, district_name) ON v.iso_code = c.iso_code AND v.city_name = ci.name
WHERE NOT EXISTS (
    SELECT 1 FROM districts d WHERE d.city_id = ci.id AND d.name = v.district_name
);
