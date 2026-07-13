-- Demo vergi tipleri ve oranları (Prompt 14 test / demo)
INSERT INTO finance.tax_types (id, code, name, is_active) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'KDV', 'Katma Değer Vergisi', TRUE),
    ('a1000000-0000-0000-0000-000000000002', 'VAT', 'Value Added Tax', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Türkiye ülke varsayılanı %20 KDV (2025-01-01 → açık)
INSERT INTO finance.tax_rates (
    id, tax_type_id, country_id, rate, start_date, is_active
) VALUES (
    'a2000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000001',
    20.00,
    '2025-01-01',
    TRUE
) ON CONFLICT DO NOTHING;

-- Lokasyon + ürün tipi özel oran %10
INSERT INTO finance.tax_rates (
    id, tax_type_id, country_id, location_id, product_type, rate, start_date, is_active
) VALUES (
    'a2000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000001',
    'b1000000-0000-0000-0000-000000000001',
    'ELECTRONIC',
    10.00,
    '2025-01-01',
    TRUE
) ON CONFLICT DO NOTHING;
