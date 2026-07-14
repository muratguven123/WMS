-- =====================================================================
-- V23: UI Kural Motoru — Yeni Bağlam Boyutları (İş İsteri 2.1, Madde 8.3)
--
-- field_behavior_rules ve column_behavior_rules tablolarına 4 yeni
-- nullable bağlam boyutu eklenir:
--   warehouse_id       → depo kırılımı (locations/zones'a mantıksal referans)
--   customer_type      → örn: RETAIL, WHOLESALE, ECOMMERCE
--   product_type       → örn: STANDARD, HAZMAT, COLD_CHAIN
--   transaction_status → örn: DRAFT, APPROVED, SHIPPED
--
-- Eşleşme semantiği DEĞİŞMEZ: NULL boyut = "herkes/her durum için geçerli".
-- Mevcut kurallar yeni kolonlarda NULL kalır → davranışları aynen korunur.
--
-- warehouse_id'ye bilinçli olarak FK tanımlanmaz: V16'daki karara uygun
-- şekilde nullable bağlam kolonları DB constraint'i taşımaz.
-- customer_type/product_type/transaction_status açık kod kümeleridir
-- (enum DEĞİL); normalizasyon (trim + UPPER) servis katmanında yapılır.
--
-- ── CACHE FLUSH MIGRATION NOTU ───────────────────────────────────────
-- UiContext.cacheKeySuffix() bu sürümle 5 segmentten 9 segmente çıkar:
--   eski: ui:{screen}:{loc}:{role}:{comp}:{country}:{op}
--   yeni: ui:{screen}:{loc}:{role}:{comp}:{country}:{op}:{wh}:{custType}:{prodType}:{txnStatus}
-- Yeni kod eski key formatını asla OKUMAZ → stale/yanlış şema dönme riski
-- yoktur; eski girdiler 30 dk TTL ile kendiliğinden düşer. Belleği hemen
-- geri kazanmak için deploy sırasında opsiyonel olarak çalıştırın:
--   redis-cli --scan --pattern 'ui:*' | xargs -r redis-cli DEL
-- =====================================================================

-- ── field_behavior_rules ──────────────────────────────────────────────

ALTER TABLE field_behavior_rules
    ADD COLUMN warehouse_id       BIGINT,
    ADD COLUMN customer_type      VARCHAR(50),
    ADD COLUMN product_type       VARCHAR(50),
    ADD COLUMN transaction_status VARCHAR(50);

COMMENT ON COLUMN field_behavior_rules.warehouse_id       IS 'NULL → tüm depolar. locations/zones''a mantıksal referans (FK yok, bkz. V16)';
COMMENT ON COLUMN field_behavior_rules.customer_type      IS 'NULL → tüm müşteri tipleri; örn: RETAIL, WHOLESALE, ECOMMERCE';
COMMENT ON COLUMN field_behavior_rules.product_type       IS 'NULL → tüm ürün tipleri; örn: STANDARD, HAZMAT, COLD_CHAIN';
COMMENT ON COLUMN field_behavior_rules.transaction_status IS 'NULL → tüm işlem durumları; örn: DRAFT, APPROVED, SHIPPED';

-- Mevcut index stratejisiyle uyumlu tekil kolon index'leri
-- (idx_fbr_location/role/company/country ile aynı desen)
CREATE INDEX idx_fbr_warehouse     ON field_behavior_rules (warehouse_id);
CREATE INDEX idx_fbr_customer_type ON field_behavior_rules (customer_type);
CREATE INDEX idx_fbr_product_type  ON field_behavior_rules (product_type);
CREATE INDEX idx_fbr_txn_status    ON field_behavior_rules (transaction_status);

-- Öncelik hiyerarşisi yorumunu güncelle
COMMENT ON COLUMN field_behavior_rules.priority IS
    'Spesifiklik eşitliğinde büyük değer kazanır. Depo=60, Lokasyon=50, Rol=40, MüşteriTipi=36, ÜrünTipi=34, İşlemDurumu=32, Şirket=30, Ülke=20, Global=10';

-- ── column_behavior_rules ─────────────────────────────────────────────

ALTER TABLE column_behavior_rules
    ADD COLUMN warehouse_id       BIGINT,
    ADD COLUMN customer_type      VARCHAR(50),
    ADD COLUMN product_type       VARCHAR(50),
    ADD COLUMN transaction_status VARCHAR(50);

COMMENT ON COLUMN column_behavior_rules.warehouse_id       IS 'NULL → tüm depolar. locations/zones''a mantıksal referans (FK yok)';
COMMENT ON COLUMN column_behavior_rules.customer_type      IS 'NULL → tüm müşteri tipleri; örn: RETAIL, WHOLESALE, ECOMMERCE';
COMMENT ON COLUMN column_behavior_rules.product_type       IS 'NULL → tüm ürün tipleri; örn: STANDARD, HAZMAT, COLD_CHAIN';
COMMENT ON COLUMN column_behavior_rules.transaction_status IS 'NULL → tüm işlem durumları; örn: DRAFT, APPROVED, SHIPPED';

CREATE INDEX idx_cbr_warehouse     ON column_behavior_rules (warehouse_id);
CREATE INDEX idx_cbr_customer_type ON column_behavior_rules (customer_type);
CREATE INDEX idx_cbr_product_type  ON column_behavior_rules (product_type);
CREATE INDEX idx_cbr_txn_status    ON column_behavior_rules (transaction_status);
