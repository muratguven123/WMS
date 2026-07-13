package com.wms.integration.service;

import com.wms.integration.api.dto.RetryResponse;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.IntegrationStatus;
import com.wms.integration.entity.enums.OutboxStatus;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.OutboxMessageRepository;
import com.wms.integration.service.IntegrationLogQueryService.IntegrationLogNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Manuel yeniden deneme (Force Retry) servisi.
 *
 * <h3>İş Akışı</h3>
 * <ol>
 *   <li>IntegrationLog kaydını bul — sadece FAILED veya RETRYING durumundakiler
 *       yeniden tetiklenebilir.</li>
 *   <li>Logdaki {@code outboxMessageId} ile OutboxMessage'ı bul.</li>
 *   <li>OutboxMessage durumunu {@code PENDING}, {@code nextAttemptAt}'i {@code now()},
 *       {@code retryCount}'u sıfırla (admin müdahalesi = yeni şans).</li>
 *   <li>IntegrationLog durumunu {@code RETRYING} olarak güncelle.</li>
 *   <li>Outbox Worker sonraki 5 sn içinde bu kaydı bulup işler.</li>
 * </ol>
 *
 * <p>Tüm güncellemeler tek {@code @Transactional} kapsamında atomik olarak gerçekleşir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationRetryService {

    private final IntegrationLogRepository    logRepository;
    private final OutboxMessageRepository     outboxMessageRepository;

    /**
     * Belirtilen log kaydını manuel olarak yeniden tetikler.
     *
     * @param logId IntegrationLog Long
     * @return retry zamanlaması ve outbox bilgisi
     * @throws IntegrationLogNotFoundException     log bulunamazsa
     * @throws RetryNotAllowedException            log durumu retry'a uygun değilse
     * @throws OutboxMessageNotFoundException      ilişkili Outbox mesajı bulunamazsa
     */
    @Transactional
    public RetryResponse forceRetry(Long logId) {
        // 1. Log kaydını bul
        IntegrationLog integrationLog = logRepository.findById(logId)
                .orElseThrow(() -> new IntegrationLogNotFoundException(logId));

        // 2. Durum kontrolü — yalnızca hata durumundakiler retry edilebilir
        if (!isRetryable(integrationLog.getStatus())) {
            throw new RetryNotAllowedException(logId, integrationLog.getStatus());
        }

        // 3. İlişkili OutboxMessage'ı bul
        Long outboxId = integrationLog.getOutboxMessageId();
        if (outboxId == null) {
            throw new OutboxMessageNotFoundException(
                    "IntegrationLog id=" + logId + " has no associated outboxMessageId");
        }

        OutboxMessage outboxMessage = outboxMessageRepository.findById(outboxId)
                .orElseThrow(() -> new OutboxMessageNotFoundException(
                        "OutboxMessage not found: id=" + outboxId));

        // 4. OutboxMessage'ı sıfırla — Worker hemen işleyecek
        OffsetDateTime now = OffsetDateTime.now();
        outboxMessage.setStatus(OutboxStatus.PENDING);
        outboxMessage.setNextAttemptAt(now);
        outboxMessage.setRetryCount(0);
        outboxMessage.setErrorMessage(null);
        outboxMessageRepository.save(outboxMessage);

        // 5. IntegrationLog durumunu güncelle
        integrationLog.setStatus(IntegrationStatus.RETRYING);
        integrationLog.setLastAttemptAt(now);
        logRepository.save(integrationLog);

        log.info("[ForceRetry] logId={} → OutboxMessage id={} reset to PENDING at {}",
                logId, outboxId, now);

        return RetryResponse.builder()
                .logId(logId)
                .outboxMessageId(outboxId)
                .message("Integration job re-queued. Outbox Worker will process it within 5 seconds.")
                .scheduledAt(now)
                .build();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private boolean isRetryable(IntegrationStatus status) {
        return status == IntegrationStatus.FAILED
                || status == IntegrationStatus.RETRYING;
    }

    // -----------------------------------------------------------------------
    // Inner exceptions
    // -----------------------------------------------------------------------

    public static class RetryNotAllowedException extends RuntimeException {
        public RetryNotAllowedException(Long logId, IntegrationStatus currentStatus) {
            super("Retry not allowed for logId=" + logId
                    + ". Current status: " + currentStatus
                    + ". Only FAILED or RETRYING logs can be retried.");
        }
    }

    public static class OutboxMessageNotFoundException extends RuntimeException {
        public OutboxMessageNotFoundException(String message) {
            super(message);
        }
    }
}
