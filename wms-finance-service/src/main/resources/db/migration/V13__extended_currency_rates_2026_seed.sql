-- =============================================================================
-- V13: UI'da listelenen tüm dövizler için TRY bazlı demo kurlar (2026-07-06/07)
--
-- V10 yalnızca EUR/USD kurlarını seed'lemişti; AED/SAR/GBP/JPY/CHF/CNY için
-- kayıt olmadığından lookup 404 dönüyordu. TCMB kontratı: foreign → TRY.
-- =============================================================================

INSERT INTO finance.currencies (code, symbol, decimal_places, name) VALUES
    ('CHF', 'Fr', 2, 'Swiss Franc'),
    ('CNY', '¥', 2, 'Chinese Yuan')
ON CONFLICT (code) DO NOTHING;

INSERT INTO finance.exchange_rates
    (source_currency_id, target_currency_id, rate_date, rate_type, rate, rate_source, created_at)
SELECT s.id, t.id, v.rate_date::date, v.rate_type, v.rate, v.rate_source, now()
FROM (VALUES
    -- GBP / TRY
    ('GBP', 'TRY', '2026-07-06', 'BUYING',           43.800000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-06', 'SELLING',          44.000000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  43.750000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 44.050000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-07', 'BUYING',           43.850000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-07', 'SELLING',          44.050000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-07', 'EFFECTIVE_BUYING',  43.800000, 'TCMB'),
    ('GBP', 'TRY', '2026-07-07', 'EFFECTIVE_SELLING', 44.100000, 'TCMB'),
    -- SAR / TRY
    ('SAR', 'TRY', '2026-07-06', 'BUYING',           8.620000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-06', 'SELLING',          8.640000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  8.600000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 8.660000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-07', 'BUYING',           8.625000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-07', 'SELLING',          8.645000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-07', 'EFFECTIVE_BUYING',  8.605000, 'TCMB'),
    ('SAR', 'TRY', '2026-07-07', 'EFFECTIVE_SELLING', 8.665000, 'TCMB'),
    -- AED / TRY
    ('AED', 'TRY', '2026-07-06', 'BUYING',           8.870000, 'TCMB'),
    ('AED', 'TRY', '2026-07-06', 'SELLING',          8.890000, 'TCMB'),
    ('AED', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  8.850000, 'TCMB'),
    ('AED', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 8.910000, 'TCMB'),
    ('AED', 'TRY', '2026-07-07', 'BUYING',           8.875000, 'TCMB'),
    ('AED', 'TRY', '2026-07-07', 'SELLING',          8.895000, 'TCMB'),
    ('AED', 'TRY', '2026-07-07', 'EFFECTIVE_BUYING',  8.855000, 'TCMB'),
    ('AED', 'TRY', '2026-07-07', 'EFFECTIVE_SELLING', 8.915000, 'TCMB'),
    -- JPY / TRY (1 JPY başına TRY)
    ('JPY', 'TRY', '2026-07-06', 'BUYING',           0.218000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-06', 'SELLING',          0.220000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  0.217000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 0.221000, 'TCMB'),
    ('JPY', 'TRY', '2026-07-07', 'BUYING',           0.218500, 'TCMB'),
    ('JPY', 'TRY', '2026-07-07', 'SELLING',          0.220500, 'TCMB'),
    ('JPY', 'TRY', '2026-07-07', 'EFFECTIVE_BUYING',  0.217500, 'TCMB'),
    ('JPY', 'TRY', '2026-07-07', 'EFFECTIVE_SELLING', 0.221500, 'TCMB'),
    -- CHF / TRY
    ('CHF', 'TRY', '2026-07-06', 'BUYING',           39.500000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-06', 'SELLING',          39.700000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  39.450000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 39.750000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-07', 'BUYING',           39.550000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-07', 'SELLING',          39.750000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-07', 'EFFECTIVE_BUYING',  39.500000, 'TCMB'),
    ('CHF', 'TRY', '2026-07-07', 'EFFECTIVE_SELLING', 39.800000, 'TCMB'),
    -- CNY / TRY
    ('CNY', 'TRY', '2026-07-06', 'BUYING',           4.480000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-06', 'SELLING',          4.500000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  4.470000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 4.510000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-07', 'BUYING',           4.485000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-07', 'SELLING',          4.505000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-07', 'EFFECTIVE_BUYING',  4.475000, 'TCMB'),
    ('CNY', 'TRY', '2026-07-07', 'EFFECTIVE_SELLING', 4.515000, 'TCMB')
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
