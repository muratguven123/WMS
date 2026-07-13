package com.wms.finance.integration.tcmb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link TcmbSyncNotificationService} mock implementasyonu.
 *
 * <p>Üretimde bu sınıf; e-posta servisi (Spring Mail), Slack webhook,
 * veya bir olay kuyruğu (Kafka/RabbitMQ) entegrasyonu ile değiştirilecektir.
 * Şimdilik hataları ERROR seviyesinde loglar ve bildirim kanalına stub sinyal üretir.
 */
@Slf4j
@Service
public class TcmbSyncNotificationServiceImpl implements TcmbSyncNotificationService {

    @Override
    public void notifyAdminOnSyncFailure(String errorType, String detail) {
        // TODO: Üretimde buraya e-posta / Slack / olay kuyruğu entegrasyonu eklenecek.
        //       Örnek: mailService.sendAdminAlert("TCMB Sync Hatası", detail);
        log.error("[TCMB-SYNC-ALERT] errorType={} | detail={}", errorType, detail);
    }
}
