package com.wms.core.aspect;

import com.wms.core.aspect.annotation.ValidateDynamicForm;
import com.wms.core.dto.ui.ResolvedFieldDto;
import com.wms.core.dto.ui.ResolvedScreenDto;
import com.wms.core.dto.ui.UiContext;
import com.wms.core.entity.enums.FieldBehavior;
import com.wms.core.exception.DynamicValidationException;
import com.wms.core.exception.DynamicValidationException.FieldError;
import com.wms.core.service.DynamicUiService;
import com.wms.core.service.UiContextFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@link ValidateDynamicForm} annotation'ını taşıyan controller parametrelerini
 * server-side dinamik form kurallarına göre doğrulayan AOP Aspect.
 *
 * <h3>Çalışma Akışı</h3>
 * <ol>
 *   <li>Pointcut: {@link ValidateDynamicForm} annotation'ına sahip herhangi bir
 *       public metot tetiklendiğinde araya girer ({@code @annotation} değil,
 *       parametre annotation'ı aranır).</li>
 *   <li>TenantContextHolder'dan aktif bağlamı ({@link UiContext}) oluşturur.</li>
 *   <li>{@link DynamicUiService#getResolvedScreen} ile kural setini çeker
 *       (Redis cache'den gelir — ek DB yükü minimum).</li>
 *   <li>Request body nesnesini {@code Map<String,Object>} veya Reflection ile okur.</li>
 *   <li>Her alan için iki kontrol:
 *     <ul>
 *       <li>{@code MANDATORY} → değer boş/null ise hata</li>
 *       <li>{@code validationRegex} tanımlı → regex eşleşmiyorsa hata</li>
 *     </ul>
 *   </li>
 *   <li>Toplanan hatalar boş değilse {@link DynamicValidationException} fırlatır.</li>
 * </ol>
 *
 * <h3>Request Body Tipi Desteği</h3>
 * <ul>
 *   <li>{@code Map<String, Object>} — alan değerleri doğrudan key ile okunur.</li>
 *   <li>Herhangi bir POJO — Reflection ile public/private alanlar okunur.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DynamicFormValidationAspect {

    private final DynamicUiService dynamicUiService;
    private final UiContextFactory uiContextFactory;
    private final HttpServletRequest request;

    // ── Pointcut ──────────────────────────────────────────────────────────────

    /**
     * Herhangi bir controller metodunu intercept eder; parametre listesinde
     * {@link ValidateDynamicForm} annotation'ı olan parametreyi bulur.
     *
     * <p>Spring AOP, parametre annotation'larını pointcut ifadesinde doğrudan
     * desteklemediği için {@code execution(*..controller..*(..))} ile tüm
     * controller metodları kapsanır; parametre annotation kontrolü {@code Around}
     * advice içinde yapılır.</p>
     */
    @Around("execution(* com.wms.core.controller..*(..))")
    public Object validateDynamicForm(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
        Object[]       args             = joinPoint.getArgs();

        for (int i = 0; i < paramAnnotations.length; i++) {
            ValidateDynamicForm annotation = findAnnotation(paramAnnotations[i]);
            if (annotation == null) continue;

            // Annotation bulundu — bu parametreyi doğrula
            String screenCode = annotation.screenCode();
            Object body       = args[i];

            log.debug("[DynamicValidation] Validasyon başlıyor. screenCode={} paramIndex={} bodyType={}",
                    screenCode, i, body == null ? "null" : body.getClass().getSimpleName());

            validate(screenCode, body);
            // Hata yoksa devam et (tek annotation desteklenir; ilk eşleşmede dur)
            break;
        }

        return joinPoint.proceed();
    }

    // ── Validasyon Mantığı ────────────────────────────────────────────────────

    /**
     * Kural setini çeker, request body'yi ayrıştırır, tüm alanları kontrol eder.
     * Hata varsa {@link DynamicValidationException} fırlatır.
     */
    private void validate(String screenCode, Object body) {
        // 1. HTTP isteğinden tam UiContext oluştur (schema API ile aynı boyutlar)
        UiContext context = uiContextFactory.fromCurrentRequest(request);

        // 2. Çözümlenmiş kural setini al (Redis cache'den)
        ResolvedScreenDto screen = dynamicUiService.getResolvedScreen(screenCode, context);

        // 3. Body'den alan değerlerini oku
        Map<String, Object> fieldValues = extractFieldValues(body);

        // 4. Her alan için kontrol
        List<FieldError> errors = new ArrayList<>();

        for (ResolvedFieldDto field : screen.fields()) {
            // HIDDEN alanlar sunucu tarafında işlenmez
            if (field.behavior() == FieldBehavior.HIDDEN) continue;

            Object rawValue  = fieldValues.get(field.fieldKey());
            String strValue  = rawValue != null ? rawValue.toString().strip() : null;
            boolean isEmpty  = strValue == null || strValue.isEmpty();

            // 4a. MANDATORY kontrolü
            if (field.behavior() == FieldBehavior.MANDATORY && isEmpty) {
                errors.add(new FieldError(
                        field.fieldKey(),
                        "MANDATORY_FIELD_MISSING",
                        "validation." + field.fieldKey() + ".required",
                        null
                ));
                continue; // Boş alan için regex kontrolü anlamsız
            }

            // 4b. Regex kontrolü (alan dolu ve regex tanımlı ise)
            if (!isEmpty && field.validationRegex() != null && !field.validationRegex().isBlank()) {
                boolean matches = Pattern.compile(field.validationRegex()).matcher(strValue).matches();
                if (!matches) {
                    errors.add(new FieldError(
                            field.fieldKey(),
                            "REGEX_VALIDATION_FAILED",
                            field.validationErrorMessageKey() != null
                                    ? field.validationErrorMessageKey()
                                    : "validation." + field.fieldKey() + ".invalid",
                            strValue
                    ));
                }
            }
        }

        // 5. Hata varsa toplu fırlat
        if (!errors.isEmpty()) {
            log.warn("[DynamicValidation] {} alan hatası. screenCode={} hatalar={}",
                    errors.size(), screenCode, errors.stream().map(FieldError::fieldKey).toList());
            throw new DynamicValidationException(screenCode, errors);
        }

        log.debug("[DynamicValidation] Validasyon başarılı. screenCode={}", screenCode);
    }

    // ── Body Ayrıştırma ───────────────────────────────────────────────────────

    /**
     * Request body nesnesinden alan değerlerini {@code Map<fieldKey, value>} olarak çıkarır.
     *
     * <ul>
     *   <li>{@code Map} → doğrudan döner (cast ile)</li>
     *   <li>POJO → Reflection ile tüm declared field'lar okunur</li>
     *   <li>null → boş map döner</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFieldValues(Object body) {
        if (body == null) {
            return Map.of();
        }

        if (body instanceof Map<?, ?> map) {
            // Map<String, Object> — en yaygın kullanım
            return (Map<String, Object>) map;
        }

        // POJO — Reflection ile tüm alanları oku
        Map<String, Object> values = new java.util.HashMap<>();
        Class<?> clazz = body.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    values.put(field.getName(), field.get(body));
                } catch (IllegalAccessException ex) {
                    log.warn("[DynamicValidation] Alan okunamadı: {} — {}", field.getName(), ex.getMessage());
                }
            }
            clazz = clazz.getSuperclass();
        }

        return values;
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    /** Parametre annotation dizisinden {@link ValidateDynamicForm}'u bulur. */
    private ValidateDynamicForm findAnnotation(Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (a instanceof ValidateDynamicForm vdf) return vdf;
        }
        return null;
    }
}
