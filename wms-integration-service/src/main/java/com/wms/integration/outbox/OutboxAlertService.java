package com.wms.integration.outbox;

import com.wms.integration.entity.OutboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Kalıcı hata bildirimleri için alarm servisi.
 *
 * <p>Outbox Worker bir mesaj için tüm retry haklarını tükettiğinde bu servis
 * çağrılır. Mevcut implementasyon sadece loglama yapar; production'da
 * buraya Slack/PagerDuty/e-posta entegrasyonu eklenir.
 *
 * <p>Ayrıştırılmış yapı sayesinde Worker kodu değişmeden
 * bildirim kanalı değiştirilebilir.
 */
@Slf4j
@Service
public class OutboxAlertService {

    /**
     * Kalıcı başarısız mesaj için admin alarmı gönderir.
     *
     * @param message tüm retry haklarını tüketen Outbox mesajı
     */
    public void alertMaxRetriesExceeded(OutboxMessage message) {
        // TODO: Slack webhook / PagerDuty / e-posta entegrasyonu
        log.error(
                "[OUTBOX ALERT] Message permanently failed after {} retries. " +
                "Manual intervention required. " +
                "id={}, aggregateType={}, aggregateId={}, jobCode={}, locationId={}, " +
                "lastError={}",
                message.getRetryCount(),
                message.getId(),
                message.getAggregateType(),
                message.getAggregateId(),
                message.getJobCode(),
                message.getLocationId(),
                message.getErrorMessage()
        );
    }
}
