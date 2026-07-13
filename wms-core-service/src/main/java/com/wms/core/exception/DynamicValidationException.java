package com.wms.core.exception;

import java.util.List;

/**
 * Dinamik form validasyonu sırasında bulunan alan hatalarını taşıyan exception.
 *
 * <p>Tek bir request'te birden fazla alan ihlali olabilir; bu yüzden
 * exception tüm hataları {@link FieldError} listesi olarak toplar ve
 * tek seferde döner. Bu yaklaşım, frontend'in her hatayı ayrı API çağrısıyla
 * öğrenmek zorunda kalmasını önler.</p>
 *
 * <p>{@link GlobalExceptionHandler} bu exception'ı {@code HTTP 400} olarak
 * {@code DynamicValidationErrorResponse} formatında yanıtlar.</p>
 *
 * @see com.wms.core.aspect.DynamicFormValidationAspect
 */
public class DynamicValidationException extends RuntimeException {

    private final String screenCode;
    private final List<FieldError> fieldErrors;

    public DynamicValidationException(String screenCode, List<FieldError> fieldErrors) {
        super("Dinamik form validasyonu başarısız. screenCode=%s, hata sayısı=%d"
                .formatted(screenCode, fieldErrors.size()));
        this.screenCode  = screenCode;
        this.fieldErrors = fieldErrors;
    }

    public String getScreenCode() {
        return screenCode;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    // ── İç sınıf: tek alan hatası ─────────────────────────────────────────────

    /**
     * Tek bir form alanına ait validasyon hatasını temsil eder.
     *
     * @param fieldKey             Hatalı alanın anahtarı — örn: {@code tax_number}
     * @param errorCode            Makine tarafından okunabilir hata kodu:
     *                             {@code MANDATORY_FIELD_MISSING} veya {@code REGEX_VALIDATION_FAILED}
     * @param messageKey           i18n çeviri anahtarı — frontend bu key ile kullanıcı mesajını çeker.
     *                             Boş alanlar için {@code validation.<fieldKey>.required},
     *                             regex hatası için {@link com.wms.core.entity.FieldBehaviorRule#getValidationErrorMessageKey()}
     * @param rejectedValue        Reddedilen değer (debug/log amaçlı); hassas veri içerebilir
     */
    public record FieldError(
            String fieldKey,
            String errorCode,
            String messageKey,
            String rejectedValue
    ) {}
}
