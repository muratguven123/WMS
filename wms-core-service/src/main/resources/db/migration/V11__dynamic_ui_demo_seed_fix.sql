-- V10 demo kurallarını gerçek demo UUID'leriyle hizalar.
-- country TR: aaaaaaaa-0000-0000-0000-000000000001 (V9)
-- location:    bbbbbbbb-0000-0000-0000-000000000001 (V6)

UPDATE field_behavior_rules
SET country_id = 'aaaaaaaa-0000-0000-0000-000000000001'::uuid
WHERE id = 'c1000000-0000-0000-0000-000000000001';

UPDATE field_behavior_rules
SET location_id = 'bbbbbbbb-0000-0000-0000-000000000001'::uuid
WHERE id = 'c1000000-0000-0000-0000-000000000002';
