-- Demo müşteri para birimi ayarları ve izinli USD (sözleşme CTR-DEMO-USD ile uyumlu)

UPDATE finance.customers
SET name                     = 'Demo Müşteri',
    invoicing_currency_id    = 'f1000000-0000-0000-0000-000000000003',
    rate_type                = 'SELLING',
    rate_source              = 'TCMB',
    exchange_diff_preference = 'PER_INVOICE'
WHERE id = 'f3000000-0000-0000-0000-000000000001';

INSERT INTO finance.customer_permitted_currencies (customer_id, currency_id)
VALUES ('f3000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000002')
ON CONFLICT (customer_id, currency_id) DO NOTHING;

-- Demo sözleşme tarih aralığı
UPDATE finance.contracts
SET start_date = '2026-01-01 00:00:00',
    end_date   = '2027-12-31 23:59:59'
WHERE id = 'f4000000-0000-0000-0000-000000000001';
