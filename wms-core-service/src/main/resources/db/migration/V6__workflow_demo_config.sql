-- =====================================================================
-- V6: Demo lokasyon için INBOUND süreç konfigürasyonu
-- Sabit demo location UUID — geliştirme/test ortamı için
-- =====================================================================

-- Demo lokasyon UUID (locations tablosunda kayıt olmasa da çalışır — loose coupling)
-- bbbbbbbb-0000-0000-0000-000000000001

INSERT INTO location_process_configs (id, location_id, process_definition_id, is_active)
SELECT
    'cccccccc-0000-0000-0000-000000000001'::uuid,
    'bbbbbbbb-0000-0000-0000-000000000001'::uuid,
    pd.id,
    TRUE
FROM process_definitions pd
WHERE pd.code = 'INBOUND'
ON CONFLICT (location_id, process_definition_id) DO NOTHING;

-- INBOUND adımları için lokasyon bazlı konfigürasyon
INSERT INTO location_process_step_configs (
    location_process_config_id,
    process_step_definition_id,
    sequence,
    is_mandatory,
    requires_approval,
    error_strategy,
    is_active
)
SELECT
    'cccccccc-0000-0000-0000-000000000001'::uuid,
    psd.id,
    steps.seq,
    steps.mandatory,
    steps.requires_approval,
    steps.error_strategy::error_strategy,
    TRUE
FROM process_step_definitions psd
JOIN process_definitions pd ON pd.id = psd.process_definition_id
JOIN (VALUES
    ('RECEIVING',      10, TRUE,  FALSE, 'BLOCK'),
    ('QC',             20, TRUE,  TRUE,  'BLOCK'),
    ('SERIAL_CONTROL', 30, FALSE, FALSE, 'BYPASS'),
    ('CUSTOMS_CONTROL',40, FALSE, FALSE, 'BYPASS'),
    ('PUTAWAY',        50, TRUE,  FALSE, 'ROUTE_TO_QUARANTINE')
) AS steps(code, seq, mandatory, requires_approval, error_strategy)
    ON psd.code = steps.code AND pd.code = 'INBOUND'
ON CONFLICT (location_process_config_id, sequence) DO NOTHING;
