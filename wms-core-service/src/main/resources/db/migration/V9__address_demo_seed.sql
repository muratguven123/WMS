-- =============================================================================
-- Demo hiyerarşik adres master verisi
-- TR: İstanbul → Kadıköy → Caferağa (eyaletsiz ülke akışı)
-- US: California → San Francisco (eyaletli ülke akışı)
-- =============================================================================

-- Türkiye
INSERT INTO countries (id, iso_code, name, is_active)
SELECT 'aaaaaaaa-0000-0000-0000-000000000001'::uuid, 'TR', 'Türkiye', TRUE
WHERE NOT EXISTS (SELECT 1 FROM countries WHERE iso_code = 'TR');

INSERT INTO cities (id, country_id, state_province_id, name, is_active)
SELECT 'aaaaaaaa-0000-0000-0000-000000000002'::uuid,
       c.id, NULL, 'İstanbul', TRUE
FROM countries c
WHERE c.iso_code = 'TR'
  AND NOT EXISTS (
      SELECT 1 FROM cities ci
      WHERE ci.country_id = c.id AND ci.name = 'İstanbul'
  );

INSERT INTO districts (id, city_id, name, is_active)
SELECT 'aaaaaaaa-0000-0000-0000-000000000003'::uuid,
       ci.id, 'Kadıköy', TRUE
FROM cities ci
JOIN countries c ON c.id = ci.country_id
WHERE c.iso_code = 'TR' AND ci.name = 'İstanbul'
  AND NOT EXISTS (
      SELECT 1 FROM districts d
      WHERE d.city_id = ci.id AND d.name = 'Kadıköy'
  );

INSERT INTO neighborhoods (id, district_id, name, zip_code, is_active)
SELECT 'aaaaaaaa-0000-0000-0000-000000000004'::uuid,
       d.id, 'Caferağa', '34710', TRUE
FROM districts d
JOIN cities ci ON ci.id = d.city_id
JOIN countries c ON c.id = ci.country_id
WHERE c.iso_code = 'TR' AND ci.name = 'İstanbul' AND d.name = 'Kadıköy'
  AND NOT EXISTS (
      SELECT 1 FROM neighborhoods n
      WHERE n.district_id = d.id AND n.name = 'Caferağa'
  );

-- ABD (eyaletli akış demo)
INSERT INTO countries (id, iso_code, name, is_active)
SELECT 'aaaaaaaa-0000-0000-0000-000000000010'::uuid, 'US', 'United States', TRUE
WHERE NOT EXISTS (SELECT 1 FROM countries WHERE iso_code = 'US');

INSERT INTO state_provinces (id, country_id, name, code, is_active)
SELECT 'aaaaaaaa-0000-0000-0000-000000000011'::uuid,
       c.id, 'California', 'CA', TRUE
FROM countries c
WHERE c.iso_code = 'US'
  AND NOT EXISTS (
      SELECT 1 FROM state_provinces sp
      WHERE sp.country_id = c.id AND sp.code = 'CA'
  );

INSERT INTO cities (id, country_id, state_province_id, name, is_active)
SELECT 'aaaaaaaa-0000-0000-0000-000000000012'::uuid,
       c.id, sp.id, 'San Francisco', TRUE
FROM countries c
JOIN state_provinces sp ON sp.country_id = c.id AND sp.code = 'CA'
WHERE c.iso_code = 'US'
  AND NOT EXISTS (
      SELECT 1 FROM cities ci
      WHERE ci.state_province_id = sp.id AND ci.name = 'San Francisco'
  );
