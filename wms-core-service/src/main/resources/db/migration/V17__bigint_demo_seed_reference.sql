-- =============================================================================
-- V17: Demo seed BIGINT ID referansı
--
-- V16 migration mevcut UUID seed kayıtlarını BIGINT'e dönüştürür.
-- Bu dosya, UI offline demo ve entegrasyon testleri için kanonik BIGINT
-- ID eşlemesini id_legacy_map üzerinden belgeler ve eksik demo kayıtları
-- sabit ID'lerle tamamlar.
--
-- Kanonik demo ID'ler (V14 UUID → BIGINT):
--   organization  1  (11111111-0000-0000-0000-000000000001)
--   company TR    1  (22222222-0000-0000-0000-000000000001)
--   company DE    2  (22222222-0000-0000-0000-000000000002)
--   location TR   1  (bbbbbbbb-0000-0000-0000-000000000001)
--   location DE   2  (bbbbbbbb-0000-0000-0000-000000000002)
--   country TR    1  (aaaaaaaa-0000-0000-0000-000000000001)
--   country DE    2  (aaaaaaaa-0000-0000-0000-000000000020)
-- =============================================================================

COMMENT ON TABLE id_legacy_map IS
    'UUID→BIGINT geçiş eşleme tablosu. Downstream servisler ve UI offline demo bu tabloyu referans alır.';

-- Demo organizasyon adını güncelle (idempotent)
UPDATE organizations SET name = 'Global WMS Holding'
WHERE id = (SELECT new_id FROM id_legacy_map
            WHERE entity_type = 'organization'
              AND old_uuid = '11111111-0000-0000-0000-000000000001'::uuid);

UPDATE companies SET name = 'Logistics Corp TR'
WHERE id = (SELECT new_id FROM id_legacy_map
            WHERE entity_type = 'company'
              AND old_uuid = '22222222-0000-0000-0000-000000000001'::uuid);

UPDATE companies SET name = 'Logistics Corp DE'
WHERE id = (SELECT new_id FROM id_legacy_map
            WHERE entity_type = 'company'
              AND old_uuid = '22222222-0000-0000-0000-000000000002'::uuid);

UPDATE locations SET name = 'İstanbul Tuzla Deposu'
WHERE id = (SELECT new_id FROM id_legacy_map
            WHERE entity_type = 'location'
              AND old_uuid = 'bbbbbbbb-0000-0000-0000-000000000001'::uuid);

UPDATE locations SET name = 'Berlin Central Deposu'
WHERE id = (SELECT new_id FROM id_legacy_map
            WHERE entity_type = 'location'
              AND old_uuid = 'bbbbbbbb-0000-0000-0000-000000000002'::uuid);
