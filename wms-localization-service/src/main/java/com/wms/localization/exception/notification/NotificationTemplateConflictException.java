package com.wms.localization.exception.notification;

/**
 * Şablon kodu benzersizlik ihlali gibi çakışma durumlarında fırlatılır.
 * GlobalExceptionHandler tarafından 409 + TEMPLATE_CONFLICT koduna map edilir.
 */
public class NotificationTemplateConflictException extends RuntimeException {

    public NotificationTemplateConflictException(String message) {
        super(message);
    }
}
