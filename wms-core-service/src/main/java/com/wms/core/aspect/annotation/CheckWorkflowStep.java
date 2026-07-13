package com.wms.core.aspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bir servis metodunun çalışmadan önce ilgili süreç adımının
 * lokasyon konfigürasyonunu kontrol etmesini sağlar.
 *
 * <p>{@link com.wms.core.aspect.WorkflowAspect} tarafından aşağıdaki
 * sırayla kontrol yürütülür:</p>
 * <ol>
 *   <li><b>Rol Yetki Kontrolü</b> — {@code responsibleRoleId} tanımlanmışsa
 *       kullanıcının aktif lokasyon+şirketteki rolü bu değerle karşılaştırılır.</li>
 *   <li><b>Onay Kontrolü</b> — {@code requiresApproval = true} ise işlem askıya
 *       alınır ({@link com.wms.core.entity.enums.ApprovalStatus#PENDING_APPROVAL}) ve
 *       {@link com.wms.core.exception.ApprovalRequiredException} fırlatılır.</li>
 *   <li><b>Hata Stratejisi</b> — Metot çalışırken exception oluşursa
 *       {@code errorStrategy} değerine göre BLOCK / BYPASS / ROUTE_TO_QUARANTINE
 *       stratejisi uygulanır.</li>
 * </ol>
 *
 * <h3>Kullanım Örneği</h3>
 * <pre>{@code
 * @CheckWorkflowStep(
 *     processCode      = "INBOUND",
 *     stepCode         = "QC",
 *     referenceIdParam = "receiptId",   // Long türünde metot parametresi adı
 *     referenceType    = "RECEIPT"
 * )
 * public void completeQcStep(Long receiptId, QcResultDto result) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckWorkflowStep {

    /**
     * Kontrol edilecek süreç kodu — örn: {@code "INBOUND"}, {@code "OUTBOUND"}.
     */
    String processCode();

    /**
     * Kontrol edilecek adım kodu — örn: {@code "QC"}, {@code "SERIAL_CONTROL"}.
     */
    String stepCode();

    /**
     * Onay talebi oluşturulurken referans Long olarak kullanılacak
     * metot parametresinin adı.
     * Parametre {@code Long} türünde olmalıdır.
     * <p>Örn: {@code "receiptId"} → metotta {@code Long receiptId} parametresi.</p>
     */
    String referenceIdParam() default "";

    /**
     * Onay talebinde saklanan referans nesne tipi — örn: {@code "RECEIPT"}, {@code "ORDER"}.
     * {@code referenceIdParam} boşsa bu alan dikkate alınmaz.
     */
    String referenceType() default "UNKNOWN";
}
