package com.wms.localization.exception.notification;

/**
 * Şablon kodu/ID'si ile eşleşen (aktif) bildirim şablonu bulunamadığında fırlatılır.
 * GlobalExceptionHandler tarafından 404 + TEMPLATE_NOT_FOUND koduna map edilir.
 */
public class NotificationTemplateNotFoundException extends RuntimeException {

    public NotificationTemplateNotFoundException(String message) {
        super(message);
    }

    public static NotificationTemplateNotFoundException byCode(String templateCode) {
        return new NotificationTemplateNotFoundException(
                "Bildirim şablonu bulunamadı veya aktif değil: " + templateCode);
    }

    public static NotificationTemplateNotFoundException byId(Long id) {
        return new NotificationTemplateNotFoundException(
                "Bildirim şablonu bulunamadı: id=" + id);
    }
}
