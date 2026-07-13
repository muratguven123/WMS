-- =============================================================================
-- V4: Örnek çeviri anahtarları ve TR/EN değerleri (geliştirme / demo)
-- =============================================================================

INSERT INTO translation_key (key_code, module, description) VALUES
    ('common.buttons.save',   'UI',     'Kaydet butonu'),
    ('common.buttons.cancel', 'UI',     'İptal butonu'),
    ('errors.stock.not_found','UI',     'Stok bulunamadı hatası'),
    ('report.stock.column.code',     'REPORT', 'Stok kodu kolon başlığı'),
    ('report.stock.column.name',     'REPORT', 'Stok adı kolon başlığı'),
    ('report.stock.column.quantity', 'REPORT', 'Miktar kolon başlığı')
ON CONFLICT (key_code) DO NOTHING;

-- Türkçe (varsayılan dil)
INSERT INTO translation_value (language_id, translation_key_id, value)
SELECT l.id, tk.id, v.value
FROM language l
CROSS JOIN (VALUES
    ('common.buttons.save',            'Kaydet'),
    ('common.buttons.cancel',          'İptal'),
    ('errors.stock.not_found',         'Stok bulunamadı'),
    ('report.stock.column.code',       'Stok Kodu'),
    ('report.stock.column.name',       'Stok Adı'),
    ('report.stock.column.quantity',   'Miktar')
) AS v(key_code, value)
JOIN translation_key tk ON tk.key_code = v.key_code
WHERE l.code = 'tr'
ON CONFLICT (language_id, translation_key_id) DO NOTHING;

-- İngilizce
INSERT INTO translation_value (language_id, translation_key_id, value)
SELECT l.id, tk.id, v.value
FROM language l
CROSS JOIN (VALUES
    ('common.buttons.save',            'Save'),
    ('common.buttons.cancel',          'Cancel'),
    ('errors.stock.not_found',         'Stock not found'),
    ('report.stock.column.code',       'Stock Code'),
    ('report.stock.column.name',       'Stock Name'),
    ('report.stock.column.quantity',   'Quantity')
) AS v(key_code, value)
JOIN translation_key tk ON tk.key_code = v.key_code
WHERE l.code = 'en'
ON CONFLICT (language_id, translation_key_id) DO NOTHING;
