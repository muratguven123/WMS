-- =============================================================================
-- V12: Efektif (banknot) kurları — V10 BUYING/SELLING seed'ine tamamlayıcı
--
-- TCMB kontratı: ForexBuying/ForexSelling → BUYING/SELLING;
-- BanknoteBuying/BanknoteSelling → EFFECTIVE_BUYING/EFFECTIVE_SELLING.
-- V10 yalnızca forex kurlarını eklemişti; lookup exact rateType eşleşmesi
-- gerektirdiğinden EFFECTIVE_* sorguları 404 dönüyordu.
-- Değerler V10 demo kurlarından tipik banknot spread'i ile türetilmiştir.
-- =============================================================================

INSERT INTO finance.exchange_rates
    (source_currency_id, target_currency_id, rate_date, rate_type, rate, rate_source, created_at)
SELECT s.id, t.id, v.rate_date::date, v.rate_type, v.rate, v.rate_source, now()
FROM (VALUES
    ('EUR', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  34.850000, 'TCMB'),
    ('EUR', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 35.150000, 'TCMB'),
    ('USD', 'TRY', '2026-07-06', 'EFFECTIVE_BUYING',  32.350000, 'TCMB'),
    ('USD', 'TRY', '2026-07-06', 'EFFECTIVE_SELLING', 32.650000, 'TCMB')
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
