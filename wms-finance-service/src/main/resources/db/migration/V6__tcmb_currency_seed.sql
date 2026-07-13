-- TCMB senkronizasyonu için ek para birimleri (Prompt 10.2)
INSERT INTO finance.currencies (id, code, symbol, decimal_places, name) VALUES
    ('f1000000-0000-0000-0000-000000000005', 'GBP', '£', 2, 'British Pound'),
    ('f1000000-0000-0000-0000-000000000006', 'SAR', '﷼', 2, 'Saudi Riyal'),
    ('f1000000-0000-0000-0000-000000000007', 'AED', 'د.إ', 2, 'UAE Dirham')
ON CONFLICT (code) DO NOTHING;
