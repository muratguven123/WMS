-- =============================================================================
-- WMS Integration Service — Faz 4.8 Seed
-- İş isteri 7.4: yeni ERP entegrasyon senaryoları için integration_jobs
-- kayıtları. Kodlar OutboxMessageTypes sabitleriyle birebir örtüşür.
-- =============================================================================

INSERT INTO integration_jobs (code, name, direction, is_active)
VALUES
    -- Mevcut kodda referans verilen ancak seed'i eksik kalan iş tipleri
    ('RECEIPT_SYNC',        'Goods Receipt Sync',       'OUTBOUND', TRUE),
    ('SHIPMENT_SYNC',       'Shipment Dispatch Sync',   'OUTBOUND', TRUE),
    -- İş isteri 7.4 — yeni senaryolar
    ('CUSTOMER_SYNC',       'Customer Account Sync',    'OUTBOUND', TRUE),
    ('PURCHASE_ORDER_SYNC', 'Purchase Order Sync',      'OUTBOUND', TRUE),
    ('SALES_ORDER_SYNC',    'Sales Order Sync',         'OUTBOUND', TRUE),
    ('RETURN_SYNC',         'Return Notice Sync',       'OUTBOUND', TRUE),
    ('COUNT_SYNC',          'Count Result Sync',        'OUTBOUND', TRUE),
    ('VOUCHER_SYNC',        'Accounting Voucher Sync',  'OUTBOUND', TRUE),
    ('TAX_INFO_PULL',       'Tax Info Pull',            'INBOUND',  TRUE)
ON CONFLICT (code) DO NOTHING;
