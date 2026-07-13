-- İş İsteri 17 — country_address_template denetim kolonları
ALTER TABLE localization.country_address_template
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

COMMENT ON COLUMN localization.country_address_template.updated_at IS 'Son şablon değişikliği zamanı';
COMMENT ON COLUMN localization.country_address_template.updated_by IS 'Son değişikliği yapan JWT subject';
