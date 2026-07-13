package com.wms.integration.entity.enums;

/**
 * Outbox mesajının yaşam döngüsü durumları.
 *
 * <pre>
 * PENDING           → İlk kaydedilme; henüz işlenmedi
 * PROCESSING        → Worker tarafından kilitlenip işleniyor (kısa sürer)
 * COMPLETED         → ERP başarılı yanıt verdi
 * FAILED            → En az 1 başarısız deneme var; retry bekliyor
 * FAILED_MAX_RETRIES→ Tüm retry hakkı tükendi; kalıcı hata (admin müdahalesi gerekir)
 * </pre>
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    FAILED_MAX_RETRIES
}
