-- =============================================================================
-- V10: Demo şirket/lokasyon para birimi ayarları + 2026 kurları + vergi oranları
--
-- Cross-service UUID referansları (wms-core-service V9/V14 ile hizalı):
--   Şirketler : 22222222-…001 Logistics Corp TR | 22222222-…002 Logistics Corp DE
--   Depolar   : bbbbbbbb-…001 İstanbul Tuzla    | bbbbbbbb-…002 Berlin Central
--   Ülkeler   : aaaaaaaa-…001 TR | aaaaaaaa-…020 DE
--
-- NOT: V8'deki tax_rates satırları (country_id = c1000000-…001) core'daki hiçbir
--      ülkeye karşılık gelmeyen legacy demo verisidir; bu migration gerçek ülke
--      UUID'leriyle 2026 oranlarını ekler, legacy satırlara dokunmaz.
-- rate_type değerleri RateType enum kontratıdır: BUYING | SELLING |
-- EFFECTIVE_BUYING | EFFECTIVE_SELLING. Tüm insert'ler idempotent'tir.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Para birimleri (V2/V6'da mevcut — yeniden çalıştırılabilirlik güvencesi)
-- ---------------------------------------------------------------------------
INSERT INTO finance.currencies (id, code, symbol, decimal_places, name) VALUES
    ('f1000000-0000-0000-0000-000000000001', 'TRY', '₺', 2, 'Turkish Lira'),
    ('f1000000-0000-0000-0000-000000000002', 'USD', '$', 2, 'US Dollar'),
    ('f1000000-0000-0000-0000-000000000003', 'EUR', '€', 2, 'Euro')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Şirket bazlı temel para birimi
-- ---------------------------------------------------------------------------
INSERT INTO finance.company_currency_settings (company_id, base_currency_id)
SELECT '22222222-0000-0000-0000-000000000001'::uuid, c.id
FROM finance.currencies c WHERE c.code = 'TRY'
ON CONFLICT (company_id) DO NOTHING;

INSERT INTO finance.company_currency_settings (company_id, base_currency_id)
SELECT '22222222-0000-0000-0000-000000000002'::uuid, c.id
FROM finance.currencies c WHERE c.code = 'EUR'
ON CONFLICT (company_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Lokasyon bazlı yerel para birimi
--    Tuzla kaydı V2'de legacy şirket UUID'siyle (cccccccc-…001) açılmıştı;
--    gerçek şirkete (Logistics Corp TR) hizalanır.
-- ---------------------------------------------------------------------------
UPDATE finance.location_currency_settings
SET company_id = '22222222-0000-0000-0000-000000000001'::uuid
WHERE location_id = 'bbbbbbbb-0000-0000-0000-000000000001'::uuid
  AND company_id  = 'cccccccc-0000-0000-0000-000000000001'::uuid;

INSERT INTO finance.location_currency_settings (location_id, company_id, local_currency_id)
SELECT 'bbbbbbbb-0000-0000-0000-000000000002'::uuid,
       '22222222-0000-0000-0000-000000000002'::uuid,
       c.id
FROM finance.currencies c WHERE c.code = 'EUR'
ON CONFLICT (location_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Güncel demo kurları (2026-07-06, Pazartesi) — BUYING + SELLING
--    Değerler V2 demo kurlarıyla tutarlı büyüklüktedir (gerçek TCMB değil).
-- ---------------------------------------------------------------------------
INSERT INTO finance.exchange_rates
    (source_currency_id, target_currency_id, rate_date, rate_type, rate, rate_source, created_at)
SELECT s.id, t.id, v.rate_date::date, v.rate_type, v.rate, v.rate_source, now()
FROM (VALUES
    ('EUR', 'TRY', '2026-07-06', 'BUYING',  34.900000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-06', 'SELLING', 35.100000, 'TCMB'),
    ('USD', 'TRY', '2026-07-06', 'BUYING',  32.400000, 'TCMB'),
    ('USD', 'TRY', '2026-07-06', 'SELLING', 32.600000, 'TCMB'),
    ('EUR', 'USD', '2026-07-06', 'BUYING',   1.070000, 'MANUAL'),
    ('EUR', 'USD', '2026-07-06', 'SELLING',  1.080000, 'MANUAL')
) AS v(source_code, target_code, rate_date, rate_type, rate, rate_source)
JOIN finance.currencies s ON s.code = v.source_code
JOIN finance.currencies t ON t.code = v.target_code
ON CONFLICT ON CONSTRAINT uk_exchange_rate DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Vergi tipleri (KDV/VAT V8'de mevcut; ÖTV yeni)
-- ---------------------------------------------------------------------------
INSERT INTO finance.tax_types (id, code, name, is_active) VALUES
    ('a1000000-0000-0000-0000-000000000003', 'OTV', 'Özel Tüketim Vergisi', TRUE)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. 2026 vergi oranları (start_date 2026-01-01, açık uçlu)
-- ---------------------------------------------------------------------------

-- KDV — Türkiye ülke varsayılanı %20
INSERT INTO finance.tax_rates (id, tax_type_id, country_id, rate, start_date, is_active)
SELECT 'a2000000-0000-0000-0000-000000000010'::uuid, tt.id,
       'aaaaaaaa-0000-0000-0000-000000000001'::uuid, 20.00, DATE '2026-01-01', TRUE
FROM finance.tax_types tt WHERE tt.code = 'KDV'
  AND NOT EXISTS (SELECT 1 FROM finance.tax_rates
                  WHERE id = 'a2000000-0000-0000-0000-000000000010');

-- KDV — Türkiye, gıda ürünleri indirimli %10
INSERT INTO finance.tax_rates (id, tax_type_id, country_id, product_type, rate, start_date, is_active)
SELECT 'a2000000-0000-0000-0000-000000000011'::uuid, tt.id,
       'aaaaaaaa-0000-0000-0000-000000000001'::uuid, 'FOOD', 10.00, DATE '2026-01-01', TRUE
FROM finance.tax_types tt WHERE tt.code = 'KDV'
  AND NOT EXISTS (SELECT 1 FROM finance.tax_rates
                  WHERE id = 'a2000000-0000-0000-0000-000000000011');

-- ÖTV — Türkiye, elektronik ürünler %20
INSERT INTO finance.tax_rates (id, tax_type_id, country_id, product_type, rate, start_date, is_active)
SELECT 'a2000000-0000-0000-0000-000000000012'::uuid, tt.id,
       'aaaaaaaa-0000-0000-0000-000000000001'::uuid, 'ELECTRONIC', 20.00, DATE '2026-01-01', TRUE
FROM finance.tax_types tt WHERE tt.code = 'OTV'
  AND NOT EXISTS (SELECT 1 FROM finance.tax_rates
                  WHERE id = 'a2000000-0000-0000-0000-000000000012');

-- VAT — Almanya ülke varsayılanı %19
INSERT INTO finance.tax_rates (id, tax_type_id, country_id, rate, start_date, is_active)
SELECT 'a2000000-0000-0000-0000-000000000013'::uuid, tt.id,
       'aaaaaaaa-0000-0000-0000-000000000020'::uuid, 19.00, DATE '2026-01-01', TRUE
FROM finance.tax_types tt WHERE tt.code = 'VAT'
  AND NOT EXISTS (SELECT 1 FROM finance.tax_rates
                  WHERE id = 'a2000000-0000-0000-0000-000000000013');

-- VAT — Almanya, gıda ürünleri indirimli %7
INSERT INTO finance.tax_rates (id, tax_type_id, country_id, product_type, rate, start_date, is_active)
SELECT 'a2000000-0000-0000-0000-000000000014'::uuid, tt.id,
       'aaaaaaaa-0000-0000-0000-000000000020'::uuid, 'FOOD', 7.00, DATE '2026-01-01', TRUE
FROM finance.tax_types tt WHERE tt.code = 'VAT'
  AND NOT EXISTS (SELECT 1 FROM finance.tax_rates
                  WHERE id = 'a2000000-0000-0000-0000-000000000014');
