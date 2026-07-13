-- =====================================================================
-- V8: Hiyerarşik Adres Master Veri — Performans İndeksleri
--
-- V1'de tanımlanan FK indekslerine ek olarak, adres arama, admin panel
-- filtreleme ve raporlama sorgularını destekleyen name / is_active /
-- zip_code indeksleri eklenir.
-- =====================================================================

-- ---------------------------------------------------------------
-- COUNTRIES
-- ---------------------------------------------------------------
CREATE INDEX idx_country_name      ON countries (name);
CREATE INDEX idx_country_is_active ON countries (is_active);

-- ---------------------------------------------------------------
-- STATE_PROVINCES  (idx_state_country V1'de zaten mevcut)
-- ---------------------------------------------------------------
CREATE INDEX idx_state_name      ON state_provinces (name);
CREATE INDEX idx_state_is_active ON state_provinces (is_active);

-- ---------------------------------------------------------------
-- CITIES  (idx_city_country, idx_city_state V1'de zaten mevcut)
-- ---------------------------------------------------------------
CREATE INDEX idx_city_name      ON cities (name);
CREATE INDEX idx_city_is_active ON cities (is_active);

-- ---------------------------------------------------------------
-- DISTRICTS  (idx_district_city V1'de zaten mevcut)
-- ---------------------------------------------------------------
CREATE INDEX idx_district_name      ON districts (name);
CREATE INDEX idx_district_is_active ON districts (is_active);

-- ---------------------------------------------------------------
-- NEIGHBORHOODS  (idx_neighborhood_district V1'de zaten mevcut)
-- ---------------------------------------------------------------
CREATE INDEX idx_neighborhood_name      ON neighborhoods (name);
CREATE INDEX idx_neighborhood_zip_code  ON neighborhoods (zip_code);
CREATE INDEX idx_neighborhood_is_active ON neighborhoods (is_active);
