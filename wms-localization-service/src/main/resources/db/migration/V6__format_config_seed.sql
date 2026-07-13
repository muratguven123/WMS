-- Demo ülke formatları (Türkiye + ABD)
INSERT INTO country_format_config (
    country_id, date_format, time_format, decimal_separator, thousand_separator
)
VALUES
    ('cccccccc-0000-0000-0000-000000000001'::uuid, 'dd.MM.yyyy', 'HH:mm', ',', '.'),
    ('dddddddd-0000-0000-0000-000000000001'::uuid, 'MM/dd/yyyy', 'hh:mm a', '.', ',')
ON CONFLICT (country_id) DO NOTHING;
