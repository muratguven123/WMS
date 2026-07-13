package com.wms.core.aspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller metot parametresi üzerinde tanımlanır; Spring AOP aracılığıyla
 * sunucu tarafı dinamik form validasyonunu tetikler.
 *
 * <p>{@link com.wms.core.aspect.DynamicFormValidationAspect} bu annotation'ı
 * taşıyan metot çağrılmadan önce araya girer ve şu kontrolleri uygular:</p>
 * <ol>
 *   <li>Çözümlenmiş alan kurallarına göre {@code MANDATORY} alanların request
 *       body'de mevcut ve dolu olduğunu doğrular.</li>
 *   <li>Tanımlı {@code validationRegex} deseniyle alan değerini test eder.</li>
 * </ol>
 *
 * <p>Herhangi bir ihlalde {@link com.wms.core.exception.DynamicValidationException}
 * fırlatılır ve {@code GlobalExceptionHandler} bunu {@code HTTP 400} olarak döner.</p>
 *
 * <h3>Kullanım Örneği</h3>
 * <pre>{@code
 * @PostMapping("/receipts")
 * public ResponseEntity<ReceiptDto> create(
 *         @ValidateDynamicForm(screenCode = "REC_CONTROL_FORM")
 *         @RequestBody Map<String, Object> body) { ... }
 * }</pre>
 *
 * <p>Request body parametresi {@code Map<String, Object>} veya herhangi bir POJO
 * olabilir. POJO durumunda Aspect, alanları Reflection ile okur.</p>
 *
 * @see com.wms.core.aspect.DynamicFormValidationAspect
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateDynamicForm {

    /**
     * Doğrulanacak ekranın kodu — örn: {@code "REC_CONTROL_FORM"}, {@code "MAT_CARD_FORM"}.
     * Bu kod üzerinden {@link com.wms.core.service.DynamicUiService} kural setini çeker.
     */
    String screenCode();
}
