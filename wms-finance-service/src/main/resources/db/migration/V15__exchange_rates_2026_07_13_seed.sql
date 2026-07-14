-- =============================================================================
-- V15: Güncel demo kurları (2026-07-10 Cuma + 2026-07-13 Pazartesi)
--
-- UI varsayılanı LocalDate.now(); V10/V13 seed'leri 2026-07-06/07'de kalmıştı.
-- MAX_FALLBACK_DAYS=5 olduğundan 13 Temmuz sorgusu 8 Temmuz'dan eski kurları
-- görmüyordu → aktif kur listesi boş + nested TX rollback 500.
-- =============================================================================

INSERT INTO finance.exchange_rates
    (source_currency_id, target_currency_id, rate_date, rate_type, rate, rate_source, created_at)
SELECT s.id, t.id, v.rate_date::date, v.rate_type, v.rate, v.rate_source, now()
FROM (VALUES
    -- EUR / TRY
    ('EUR', 'TRY', '2026-07-10', 'BUYING',           35.050000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-10', 'SELLING',          35.250000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  35.000000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 35.300000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-13', 'BUYING',           35.100000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-13', 'SELLING',          35.300000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  35.050000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 35.350000, 'TCMB'),
    -- USD / TRY
    ('USD', 'TRY', '2026-07-10', 'BUYING',           32.550000, 'TCMB'),
    ('USD', 'TRY', '2026-07-10', 'SELLING',          32.750000, 'TCMB'),
    ('USD', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  32.500000, 'TCMB'),
    ('USD', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 32.800000, 'TCMB'),
    ('USD', 'TRY', '2026-07-13', 'BUYING',           32.600000, 'TCMB'),
    ('USD', 'TRY', '2026-07-13', 'SELLING',          32.800000, 'TCMB'),
    ('USD', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  32.550000, 'TCMB'),
    ('USD', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 32.850000, 'TCMB'),
    -- GBP / TRY
    ('GBP', 'TRY', '2026-07-10', 'BUYING',           44.000000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-10', 'SELLING',          44.200000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  43.950000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 44.250000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-13', 'BUYING',           44.050000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-13', 'SELLING',          44.250000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  44.000000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 44.300000, 'TCMB'),
    -- SAR / TRY
    ('SAR', 'TRY', '2026-07-10', 'BUYING',           8.660000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-10', 'SELLING',          8.680000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  8.640000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 8.700000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-13', 'BUYING',           8.665000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-13', 'SELLING',          8.685000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  8.645000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 8.705000, 'TCMB'),
    -- AED / TRY
    ('AED', 'TRY', '2026-07-10', 'BUYING',           8.910000, 'TCMB'),
    ('AED', 'TRY', '2026-07-10', 'SELLING',          8.930000, 'TCMB'),
    ('AED', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  8.890000, 'TCMB'),
    ('AED', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 8.950000, 'TCMB'),
    ('AED', 'TRY', '2026-07-13', 'BUYING',           8.915000, 'TCMB'),
    ('AED', 'TRY', '2026-07-13', 'SELLING',          8.935000, 'TCMB'),
    ('AED', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  8.895000, 'TCMB'),
    ('AED', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 8.955000, 'TCMB'),
    -- JPY / TRY
    ('JPY', 'TRY', '2026-07-10', 'BUYING',           0.221000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-10', 'SELLING',          0.223000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  0.220000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 0.224000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-13', 'BUYING',           0.221500, 'TCMB'),
    ('JPY', 'TRY', '2026-07-13', 'SELLING',          0.223500, 'TCMB'),
    ('JPY', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  0.220500, 'TCMB'),
    ('JPY', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 0.224500, 'TCMB'),
    -- CHF / TRY
    ('CHF', 'TRY', '2026-07-10', 'BUYING',           39.700000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-10', 'SELLING',          39.900000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  39.650000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 39.950000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-13', 'BUYING',           39.750000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-13', 'SELLING',          39.950000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  39.700000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 40.000000, 'TCMB'),
    -- CNY / TRY
    ('CNY', 'TRY', '2026-07-10', 'BUYING',           4.520000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-10', 'SELLING',          4.540000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-10', 'EFFECTIVE_BUYING',  4.510000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-10', 'EFFECTIVE_SELLING', 4.550000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-13', 'BUYING',           4.525000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-13', 'SELLING',          4.545000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-13', 'EFFECTIVE_BUYING',  4.515000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-13', 'EFFECTIVE_SELLING', 4.555000, 'TCMB')
) AS v(source_code, target_code, rate_date, rate_type, rate, rate_source)
JOIN finance.currencies s ON s.code = v.source_code
JOIN finance.currencies t ON t.code = v.target_code
WHERE NOT EXISTS (
    SELECT 1
    FROM finance.exchange_rates er
    WHERE er.source_currency_id = s.id
      AND er.target_currency_id = t.id
      AND er.rate_date = v.rate_date::date
      AND er.rate_type = v.rate_type
      AND er.rate_source = v.rate_source
);
