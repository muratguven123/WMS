-- Kur farkı kaydına vergi tutarı alanları
ALTER TABLE exchange_difference_logs
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS tax_type_code VARCHAR(32);

ALTER TABLE invoice_items
    ADD COLUMN IF NOT EXISTS tax_type_code VARCHAR(32);
