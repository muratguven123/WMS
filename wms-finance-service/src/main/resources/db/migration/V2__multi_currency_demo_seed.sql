-- Demo seed — TRY, USD, EUR, JPY + demo hierarchy + örnek kurlar

INSERT INTO finance.currencies (id, code, symbol, decimal_places, name) VALUES
    ('f1000000-0000-0000-0000-000000000001', 'TRY', '₺', 2, 'Turkish Lira'),
    ('f1000000-0000-0000-0000-000000000002', 'USD', '$', 2, 'US Dollar'),
    ('f1000000-0000-0000-0000-000000000003', 'EUR', '€', 2, 'Euro'),
    ('f1000000-0000-0000-0000-000000000004', 'JPY', '¥', 0, 'Japanese Yen')
ON CONFLICT (code) DO NOTHING;

INSERT INTO finance.system_config (id, default_currency_id)
VALUES ('f2000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

-- wms-core V6 demo company / location
INSERT INTO finance.company_currency_settings (company_id, base_currency_id) VALUES
    ('cccccccc-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

INSERT INTO finance.location_currency_settings (location_id, company_id, local_currency_id) VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

INSERT INTO finance.customers (id, default_currency_id) VALUES
    ('f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000003')
ON CONFLICT DO NOTHING;

INSERT INTO finance.contracts (id, customer_id, currency_id, contract_code) VALUES
    ('f4000000-0000-0000-0000-000000000001', 'f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000002', 'CTR-DEMO-USD')
ON CONFLICT DO NOTHING;

-- EUR/TRY SELLING — Cuma 2026-07-03 ve önceki Perşembe (hafta sonu fallback testi için)
INSERT INTO finance.exchange_rates
    (source_currency_id, target_currency_id, rate_date, rate_type, rate, rate_source)
VALUES
    ('f1000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000001', '2026-07-03', 'SELLING', 35.000000, 'TCMB'),
    ('f1000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000001', '2026-07-02', 'SELLING', 34.900000, 'TCMB'),
    ('f1000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000001', '2026-07-03', 'SELLING', 32.500000, 'TCMB')
ON CONFLICT DO NOTHING;
