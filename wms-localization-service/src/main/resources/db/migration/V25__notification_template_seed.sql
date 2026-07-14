-- =============================================================================
-- Flyway Migration: V25__notification_template_seed.sql
-- Örnek bildirim şablonları — TR/EN (İş İsteri 2.1 — madde 5.2)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Şablon üst kayıtları
-- -----------------------------------------------------------------------------
INSERT INTO notification_template (template_code, channel, description, active, created_by, updated_by)
VALUES
    ('RECEIPT_APPROVED_MAIL',    'EMAIL', 'Mal kabul onaylandığında ilgili kullanıcıya gönderilen e-posta', TRUE, 'seed', 'seed'),
    ('SHIPMENT_DISPATCHED_MAIL', 'EMAIL', 'Sevkiyat yola çıktığında ilgili kullanıcıya gönderilen e-posta', TRUE, 'seed', 'seed')
ON CONFLICT ON CONSTRAINT uq_notification_template_code DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. RECEIPT_APPROVED_MAIL içerikleri
--    Değişkenler: userName, receiptNumber, warehouseName, approvedAt, totalQuantity
-- -----------------------------------------------------------------------------
INSERT INTO notification_template_content (template_id, language_code, subject, body, updated_by)
SELECT t.id,
       'tr',
       'Mal Kabul Onaylandı — {{receiptNumber}}',
       E'Sayın {{userName}},\n\n'
       || E'{{warehouseName}} deposundaki {{receiptNumber}} numaralı mal kabul işlemi '
       || E'{{approvedAt}} tarihinde onaylanmıştır.\n\n'
       || E'Toplam kabul edilen miktar: {{totalQuantity}}\n\n'
       || E'Detayları WMS uygulamasından inceleyebilirsiniz.\n\n'
       || E'Saygılarımızla,\nWMS Ekibi',
       'seed'
FROM notification_template t
WHERE t.template_code = 'RECEIPT_APPROVED_MAIL'
ON CONFLICT ON CONSTRAINT uq_ntc_template_language DO NOTHING;

INSERT INTO notification_template_content (template_id, language_code, subject, body, updated_by)
SELECT t.id,
       'en',
       'Goods Receipt Approved — {{receiptNumber}}',
       E'Dear {{userName}},\n\n'
       || E'Goods receipt {{receiptNumber}} at warehouse {{warehouseName}} '
       || E'was approved on {{approvedAt}}.\n\n'
       || E'Total accepted quantity: {{totalQuantity}}\n\n'
       || E'You can review the details in the WMS application.\n\n'
       || E'Best regards,\nWMS Team',
       'seed'
FROM notification_template t
WHERE t.template_code = 'RECEIPT_APPROVED_MAIL'
ON CONFLICT ON CONSTRAINT uq_ntc_template_language DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. SHIPMENT_DISPATCHED_MAIL içerikleri
--    Değişkenler: userName, shipmentNumber, warehouseName, carrierName,
--                 trackingNumber, dispatchedAt
-- -----------------------------------------------------------------------------
INSERT INTO notification_template_content (template_id, language_code, subject, body, updated_by)
SELECT t.id,
       'tr',
       'Sevkiyat Yola Çıktı — {{shipmentNumber}}',
       E'Sayın {{userName}},\n\n'
       || E'{{warehouseName}} deposundan {{shipmentNumber}} numaralı sevkiyat '
       || E'{{dispatchedAt}} tarihinde {{carrierName}} taşıyıcısıyla yola çıkmıştır.\n\n'
       || E'Takip numarası: {{trackingNumber}}\n\n'
       || E'Saygılarımızla,\nWMS Ekibi',
       'seed'
FROM notification_template t
WHERE t.template_code = 'SHIPMENT_DISPATCHED_MAIL'
ON CONFLICT ON CONSTRAINT uq_ntc_template_language DO NOTHING;

INSERT INTO notification_template_content (template_id, language_code, subject, body, updated_by)
SELECT t.id,
       'en',
       'Shipment Dispatched — {{shipmentNumber}}',
       E'Dear {{userName}},\n\n'
       || E'Shipment {{shipmentNumber}} from warehouse {{warehouseName}} was dispatched '
       || E'on {{dispatchedAt}} with carrier {{carrierName}}.\n\n'
       || E'Tracking number: {{trackingNumber}}\n\n'
       || E'Best regards,\nWMS Team',
       'seed'
FROM notification_template t
WHERE t.template_code = 'SHIPMENT_DISPATCHED_MAIL'
ON CONFLICT ON CONSTRAINT uq_ntc_template_language DO NOTHING;
