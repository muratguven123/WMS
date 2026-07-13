package com.wms.integration.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Exponential Backoff yeniden deneme politikası.
 *
 * <h3>Formül</h3>
 * <pre>
 *   nextAttemptAt = now + 2^retryCount dakika
 *
 *   retryCount=0 (ilk hata) → +1  dk
 *   retryCount=1             → +2  dk
 *   retryCount=2             → +4  dk
 *   retryCount=3 (max)       → FAILED_MAX_RETRIES
 * </pre>
 *
 * <p>Maksimum bekleme süresi {@code maxBackoffMinutes} ile sınırlandırılır;
 * çok büyük değerlerin Outbox'ı tıkaması önlenir.
 */
@Component
public class OutboxRetryPolicy {

    /** Maksimum yeniden deneme sayısı (bu sayıya ulaşıldığında kalıcı hata). */
    @Value("${outbox.retry.max-attempts:3}")
    private int maxAttempts;

    /** Backoff'un sınırlandırıldığı maksimum bekleme süresi (dakika). */
    @Value("${outbox.retry.max-backoff-minutes:60}")
    private int maxBackoffMinutes;

    /**
     * Bir sonraki deneme zamanını hesaplar.
     *
     * @param currentRetryCount mevcut deneme sayısı (henüz artırılmamış)
     * @return bir sonraki deneme zamanı (UTC)
     */
    public OffsetDateTime nextAttemptAt(int currentRetryCount) {
        long backoffMinutes = (long) Math.pow(2, currentRetryCount);
        backoffMinutes = Math.min(backoffMinutes, maxBackoffMinutes);
        return OffsetDateTime.now().plusMinutes(backoffMinutes);
    }

    /**
     * Yeniden deneme hakkı tükendi mi?
     *
     * @param retryCount mevcut deneme sayısı (başarısız denemelerden sonra artırılmış değer)
     */
    public boolean isExhausted(int retryCount) {
        return retryCount >= maxAttempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
