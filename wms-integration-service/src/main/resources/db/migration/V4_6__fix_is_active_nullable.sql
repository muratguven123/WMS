-- =============================================================================
-- V4_6: is_active NULL düzeltmesi
-- outbox_messages ve integration_logs tablolarında is_active kolonu nullable ve
-- DEFAULT'suz tanımlıydı. Entity'ler bu alanı primitive boolean (BaseEntity) +
-- insertable=false olarak map ettiği için INSERT'te NULL yazılıyor, okuma anında
-- Hibernate primitive boolean'a NULL atayamayıp JpaSystemException fırlatıyordu.
-- Bu da /api/integrations/logs listesinde HTTP 500 ve Outbox Worker çöküşüne yol açıyordu.
--
-- Çözüm: mevcut NULL'ları TRUE'ya çek ve kolona DEFAULT TRUE ver.
-- insertable=false olduğundan yeni kayıtlar DB default'undan TRUE alır, okuma asla NULL olmaz.
-- =============================================================================

UPDATE outbox_messages SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE outbox_messages ALTER COLUMN is_active SET DEFAULT TRUE;

UPDATE integration_logs SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE integration_logs ALTER COLUMN is_active SET DEFAULT TRUE;
