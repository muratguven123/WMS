-- V23: Tablo kolon başlığı çeviri anahtarları (İş İsteri 16)
INSERT INTO translation_key (key_code, module, description) VALUES
    ('columns.common.id', 'UI', 'ID column'),
    ('columns.common.actions', 'UI', 'Actions column'),
    ('columns.common.status', 'UI', 'Status column'),
    ('columns.common.date', 'UI', 'Date column'),
    ('columns.common.time', 'UI', 'Time column'),
    ('columns.common.qty', 'UI', 'Quantity column'),
    ('columns.common.sequence', 'UI', 'Sequence column'),
    ('columns.common.updatedAt', 'UI', 'Updated at column'),
    ('columns.common.createdAt', 'UI', 'Created at column'),
    ('columns.address.country', 'UI', 'Country column'),
    ('columns.address.formatted', 'UI', 'Formatted address column'),
    ('columns.country.iso', 'UI', 'Country ISO column'),
    ('columns.country.name', 'UI', 'Country name column'),
    ('columns.country.stateCount', 'UI', 'State count column'),
    ('columns.country.cityCount', 'UI', 'City count column'),
    ('columns.tax.code', 'UI', 'Tax code column'),
    ('columns.tax.rate', 'UI', 'Tax rate column'),
    ('columns.tax.effectiveDate', 'UI', 'Effective date column'),
    ('columns.tax.version', 'UI', 'Version column'),
    ('columns.tax.effectiveFrom', 'UI', 'Effective from column'),
    ('columns.fx.pair', 'UI', 'Currency pair column'),
    ('columns.fx.rate', 'UI', 'Exchange rate column'),
    ('columns.fx.source', 'UI', 'Rate source column'),
    ('columns.user.username', 'UI', 'Username column'),
    ('columns.user.email', 'UI', 'Email column'),
    ('columns.user.role', 'UI', 'Role column'),
    ('columns.audit.timestamp', 'UI', 'Audit timestamp'),
    ('columns.audit.user', 'UI', 'Audit user'),
    ('columns.audit.entity', 'UI', 'Audit entity'),
    ('columns.audit.action', 'UI', 'Audit action'),
    ('columns.audit.details', 'UI', 'Audit details'),
    ('columns.table.settings', 'UI', 'Column settings button'),
    ('columns.table.reset', 'UI', 'Reset columns to default')
ON CONFLICT (key_code) DO NOTHING;

INSERT INTO translation_value (language_id, translation_key_id, value)
SELECT l.id, tk.id, v.value
FROM language l
CROSS JOIN (VALUES
    ('columns.common.id', 'ID'),
    ('columns.common.actions', 'İşlemler'),
    ('columns.common.status', 'Durum'),
    ('columns.common.date', 'Tarih'),
    ('columns.common.sequence', 'Sıra'),
    ('columns.address.country', 'Ülke'),
    ('columns.address.formatted', 'Adres'),
    ('columns.table.settings', 'Kolonlar'),
    ('columns.table.reset', 'Varsayılana Dön')
) AS v(key_code, value)
JOIN translation_key tk ON tk.key_code = v.key_code
WHERE l.code = 'tr'
ON CONFLICT (language_id, translation_key_id) DO NOTHING;

INSERT INTO translation_value (language_id, translation_key_id, value)
SELECT l.id, tk.id, v.value
FROM language l
CROSS JOIN (VALUES
    ('columns.common.id', 'ID'),
    ('columns.common.actions', 'Actions'),
    ('columns.common.status', 'Status'),
    ('columns.common.date', 'Date'),
    ('columns.common.sequence', 'Seq'),
    ('columns.address.country', 'Country'),
    ('columns.address.formatted', 'Address'),
    ('columns.table.settings', 'Columns'),
    ('columns.table.reset', 'Reset to Default')
) AS v(key_code, value)
JOIN translation_key tk ON tk.key_code = v.key_code
WHERE l.code = 'en'
ON CONFLICT (language_id, translation_key_id) DO NOTHING;
