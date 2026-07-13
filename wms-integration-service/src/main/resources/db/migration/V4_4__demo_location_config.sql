-- =============================================================================
-- Demo lokasyon için MOCK ERP entegrasyon konfigürasyonu
-- location_id: bbbbbbbb-0000-0000-0000-000000000001 (wms-core demo lokasyonu)
-- =============================================================================

INSERT INTO location_integration_configs (
    location_id,
    integration_system_id,
    connection_type,
    connection_params,
    is_active
)
SELECT
    'bbbbbbbb-0000-0000-0000-000000000001'::uuid,
    s.id,
    'REST',
    '{"mock": true, "baseUrl": "http://localhost/mock"}'::jsonb,
    TRUE
FROM integration_systems s
WHERE s.code = 'MOCK'
  AND NOT EXISTS (
      SELECT 1 FROM location_integration_configs c
      WHERE c.location_id = 'bbbbbbbb-0000-0000-0000-000000000001'::uuid
        AND c.is_active = TRUE
  );
