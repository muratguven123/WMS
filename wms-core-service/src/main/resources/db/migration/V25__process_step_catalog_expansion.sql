-- =============================================================================
-- V25: Seed missing process step definitions
-- =============================================================================

-- INBOUND process steps
INSERT INTO process_step_definitions (process_definition_id, code, name, default_sequence, is_active, created_at)
SELECT pd.id, 'LOT_BATCH_KONTROL', 'Lot/Batch Kontrolü', 21, TRUE, now()
FROM process_definitions pd WHERE pd.code = 'INBOUND'
AND NOT EXISTS (SELECT 1 FROM process_step_definitions WHERE code = 'LOT_BATCH_KONTROL');

INSERT INTO process_step_definitions (process_definition_id, code, name, default_sequence, is_active, created_at)
SELECT pd.id, 'SKT_KONTROL', 'SKT Kontrolü', 22, TRUE, now()
FROM process_definitions pd WHERE pd.code = 'INBOUND'
AND NOT EXISTS (SELECT 1 FROM process_step_definitions WHERE code = 'SKT_KONTROL');

INSERT INTO process_step_definitions (process_definition_id, code, name, default_sequence, is_active, created_at)
SELECT pd.id, 'ETIKETLEME', 'Etiketleme', 23, TRUE, now()
FROM process_definitions pd WHERE pd.code = 'INBOUND'
AND NOT EXISTS (SELECT 1 FROM process_step_definitions WHERE code = 'ETIKETLEME');

-- RETURN process steps
INSERT INTO process_step_definitions (process_definition_id, code, name, default_sequence, is_active, created_at)
SELECT pd.id, 'SAYIM_KONTROL', 'Sayım Kontrolü', 25, TRUE, now()
FROM process_definitions pd WHERE pd.code = 'RETURN'
AND NOT EXISTS (SELECT 1 FROM process_step_definitions WHERE code = 'SAYIM_KONTROL');

-- OUTBOUND process steps
INSERT INTO process_step_definitions (process_definition_id, code, name, default_sequence, is_active, created_at)
SELECT pd.id, 'SEVKIYAT_ONAYI', 'Sevkiyat Onayı', 35, TRUE, now()
FROM process_definitions pd WHERE pd.code = 'OUTBOUND'
AND NOT EXISTS (SELECT 1 FROM process_step_definitions WHERE code = 'SEVKIYAT_ONAYI');

INSERT INTO process_step_definitions (process_definition_id, code, name, default_sequence, is_active, created_at)
SELECT pd.id, 'FINANSAL_KONTROL', 'Finansal Kontrol', 36, TRUE, now()
FROM process_definitions pd WHERE pd.code = 'OUTBOUND'
AND NOT EXISTS (SELECT 1 FROM process_step_definitions WHERE code = 'FINANSAL_KONTROL');

INSERT INTO process_step_definitions (process_definition_id, code, name, default_sequence, is_active, created_at)
SELECT pd.id, 'FATURA_KONTROL', 'Fatura Kontrolü', 37, TRUE, now()
FROM process_definitions pd WHERE pd.code = 'OUTBOUND'
AND NOT EXISTS (SELECT 1 FROM process_step_definitions WHERE code = 'FATURA_KONTROL');
