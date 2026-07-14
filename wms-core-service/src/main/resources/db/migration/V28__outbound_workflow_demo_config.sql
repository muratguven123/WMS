-- =====================================================================
-- V28: Demo lokasyon için OUTBOUND süreç konfigürasyonu
-- location_id = 1 (İstanbul Tuzla — V17 kanonik demo ID)
-- Not: uk_loc_proc_config V16 sonrası her ortamda olmayabilir → NOT EXISTS kullan
-- =====================================================================

INSERT INTO location_process_configs (location_id, process_definition_id, is_active)
SELECT
    1,
    pd.id,
    TRUE
FROM process_definitions pd
WHERE pd.code = 'OUTBOUND'
  AND NOT EXISTS (
      SELECT 1
      FROM location_process_configs existing
      WHERE existing.location_id = 1
        AND existing.process_definition_id = pd.id
  );

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
    lpc.id,
    psd.id,
    steps.seq,
    steps.mandatory,
    steps.requires_approval,
    steps.error_strategy,
    TRUE
FROM location_process_configs lpc
JOIN process_definitions pd ON pd.id = lpc.process_definition_id
JOIN process_step_definitions psd ON psd.process_definition_id = pd.id
JOIN (VALUES
    ('PICKING',  10, TRUE,  FALSE, 'BLOCK'),
    ('PACKING',  20, TRUE,  FALSE, 'BLOCK'),
    ('QC',       30, FALSE, FALSE, 'BYPASS'),
    ('SHIPPING', 40, TRUE,  FALSE, 'BLOCK')
) AS steps(code, seq, mandatory, requires_approval, error_strategy)
    ON psd.code = steps.code
WHERE lpc.location_id = 1
  AND pd.code = 'OUTBOUND'
  AND NOT EXISTS (
      SELECT 1 FROM location_process_step_configs existing
      WHERE existing.location_process_config_id = lpc.id
        AND existing.sequence = steps.seq
  );
