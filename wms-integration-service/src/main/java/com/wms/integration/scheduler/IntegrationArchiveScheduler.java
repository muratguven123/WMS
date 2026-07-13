package com.wms.integration.scheduler;

import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Eski entegrasyon log ve Outbox kayıtlarını temizleyen aylık scheduler.
 *
 * <h3>Temizleme Stratejisi</h3>
 * <ul>
 *   <li>{@code integration_logs}: {@code SUCCESS} durumundaki 30+ günlük kayıtlar silinir.
 *       {@code FAILED} kayıtlar post-mortem analiz için <b>silinmez</b>.</li>
 *   <li>{@code outbox_messages}: {@code COMPLETED} durumundaki 30+ günlük kayıtlar silinir.
 *       {@code FAILED_MAX_RETRIES} kayıtlar arşiv olarak <b>silinmez</b>.</li>
 * </ul>
 *
 * <h3>Zamanlama</h3>
 * Cron ifadesi: {@code 0 0 2 1 * *} — her ayın 1'inde saat 02:00'de çalışır.
 * {@code application.yml}'den override edilebilir:
 * {@code archive.scheduler.cron}.
 *
 * <h3>Büyük Tablo Notu</h3>
 * Milyonlarca satırlı tablolarda tek DELETE uzun sürebilir ve lock contention
 * yaratır. Production'da bu method, kayıtları chunk'lar halinde silen bir
 * döngüyle değiştirilmeli veya PostgreSQL partitioning (PARTITION BY RANGE createdAt)
 * kullanılmalıdır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationArchiveScheduler {

    private final IntegrationLogRepository  integrationLogRepository;
    private final OutboxMessageRepository   outboxMessageRepository;

    /**
     * Log arşivi retention süresi (gün). Default: 30.
     * Override: {@code archive.retention-days=60}
     */
    @Value("${archive.retention-days:30}")
    private int retentionDays;

    // -----------------------------------------------------------------------
    // Monthly cleanup job
    // -----------------------------------------------------------------------

    /**
     * Eski SUCCESS / COMPLETED kayıtları temizler.
     * Her ayın 1'inde saat 02:00'de çalışır.
     */
    @Scheduled(cron = "${archive.scheduler.cron:0 0 2 1 * *}")
    @Transactional
    public void runArchive() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        log.info("[ArchiveScheduler] Starting cleanup. cutoff={} (retentionDays={})",
                cutoff, retentionDays);

        int deletedLogs    = archiveIntegrationLogs(cutoff);
        int deletedOutbox  = archiveOutboxMessages(cutoff);

        log.info("[ArchiveScheduler] Cleanup complete. " +
                 "Deleted: integrationLogs={}, outboxMessages={}",
                deletedLogs, deletedOutbox);
    }

    // -----------------------------------------------------------------------
    // Sub-tasks
    // -----------------------------------------------------------------------

    /**
     * {@code integration_logs} tablosundan eski SUCCESS kayıtlarını siler.
     *
     * @param cutoff bu tarihten önce oluşturulan kayıtlar silinir
     * @return silinen satır sayısı
     */
    private int archiveIntegrationLogs(OffsetDateTime cutoff) {
        try {
            int deleted = integrationLogRepository.deleteOldSuccessLogs(cutoff);
            log.info("[ArchiveScheduler] integration_logs: {} SUCCESS records deleted (before {})",
                    deleted, cutoff);
            return deleted;
        } catch (Exception ex) {
            log.error("[ArchiveScheduler] Failed to archive integration_logs", ex);
            return 0;
        }
    }

    /**
     * {@code outbox_messages} tablosundan eski COMPLETED kayıtlarını siler.
     *
     * @param cutoff bu tarihten önce oluşturulan kayıtlar silinir
     * @return silinen satır sayısı
     */
    private int archiveOutboxMessages(OffsetDateTime cutoff) {
        try {
            int deleted = outboxMessageRepository.deleteOldCompletedMessages(cutoff);
            log.info("[ArchiveScheduler] outbox_messages: {} COMPLETED records deleted (before {})",
                    deleted, cutoff);
            return deleted;
        } catch (Exception ex) {
            log.error("[ArchiveScheduler] Failed to archive outbox_messages", ex);
            return 0;
        }
    }
}
