-- Onay talepleri depo/şirket bazında filtrelenebilsin
ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS company_id  BIGINT,
    ADD COLUMN IF NOT EXISTS location_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_approval_company_location
    ON approval_requests (company_id, location_id);

-- Mevcut kayıtlar: step_config üzerinden lokasyon process config'e bağlanır
UPDATE approval_requests ar
SET location_id = lpc.location_id
FROM location_process_step_configs sc
JOIN location_process_configs lpc ON lpc.id = sc.location_process_config_id
WHERE ar.step_config_id = sc.id
  AND ar.location_id IS NULL;

UPDATE approval_requests ar
SET company_id = l.company_id
FROM locations l
WHERE ar.location_id = l.id
  AND ar.company_id IS NULL;
