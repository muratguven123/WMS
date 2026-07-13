package com.wms.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dinamik form validasyon hatası için genişletilmiş API yanıt DTO'su.
 *
 * <p>{@link ErrorResponse}'un alan bazlı hata listesini taşıyan versiyonudur.
 * {@code GlobalExceptionHandler} bu DTO'yu {@link DynamicValidationException}
 * yakaladığında döner.</p>
 *
 * @param status      HTTP durum kodu (400)
 * @param error       HTTP durum açıklaması ("Bad Request")
 * @param errorCode   Makine tarafından okunabilir hata kodu ("DYNAMIC_FORM_VALIDATION_FAILED")
 * @param message     Genel hata mesajı
 * @param path        Hatanın fırlatıldığı endpoint
 * @param screenCode  Hangi ekranda validasyon hatası oluştu
 * @param fieldErrors Alan bazlı hata detayları
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DynamicValidationErrorResponse(
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        String screenCode,
        List<DynamicValidationException.FieldError> fieldErrors,
        OffsetDateTime timestamp
) {

    public DynamicValidationErrorResponse(
            int status,
            String error,
            String errorCode,
            String message,
            String path,
            String screenCode,
            List<DynamicValidationException.FieldError> fieldErrors) {

        this(status, error, errorCode, message, path, screenCode, fieldErrors, OffsetDateTime.now());
    }
}
