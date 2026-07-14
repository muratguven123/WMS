package com.wms.localization.exception.notification;

/**
 * Şablonun ne istenen dilde ne de varsayılan dilde içeriği bulunduğunda fırlatılır.
 * GlobalExceptionHandler tarafından 404 + TEMPLATE_CONTENT_NOT_FOUND koduna map edilir.
 */
public class TemplateContentNotFoundException extends RuntimeException {

    public TemplateContentNotFoundException(String templateCode,
                                            String requestedLocale,
                                            String defaultLocale) {
        super("Şablon içeriği bulunamadı → templateCode=" + templateCode
                + ", istenen dil=" + requestedLocale
                + (defaultLocale != null
                        ? ", denenen varsayılan dil=" + defaultLocale
                        : ", varsayılan dil tanımlı değil"));
    }

    public TemplateContentNotFoundException(String message) {
        super(message);
    }
}
