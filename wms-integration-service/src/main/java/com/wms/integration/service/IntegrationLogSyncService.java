package com.wms.integration.service;

import com.wms.integration.adapter.dto.ErpResponse;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.IntegrationStatus;
import com.wms.integration.messaging.IntegrationEventPublisher;
import com.wms.integration.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Outbox Worker sonuçlarını {@link IntegrationLog} kayıtlarıyla senkronize eder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationLogSyncService {

    private final IntegrationLogRepository integrationLogRepository;
    private final IntegrationEventPublisher integrationEventPublisher;

    @Transactional
    public void onOutboxSuccess(OutboxMessage message, ErpResponse response) {
        findLog(message).ifPresent(log -> {
            log.setStatus(IntegrationStatus.SUCCESS);
            log.setResponsePayload(response.getMessage());
            log.setExternalReference(response.getExternalReference());
            log.setErrorMessage(null);
            log.setRetryCount(message.getRetryCount());
            log.setLastAttemptAt(OffsetDateTime.now());
            integrationLogRepository.save(log);
            integrationEventPublisher.publishLogUpdated(log, message);
        });
    }

    @Transactional
    public void onOutboxRetryableFailure(OutboxMessage message) {
        findLog(message).ifPresent(log -> {
            log.setStatus(IntegrationStatus.RETRYING);
            log.setErrorMessage(message.getErrorMessage());
            log.setRetryCount(message.getRetryCount());
            log.setLastAttemptAt(OffsetDateTime.now());
            integrationLogRepository.save(log);
            integrationEventPublisher.publishLogUpdated(log, message);
        });
    }

    @Transactional
    public void onOutboxPermanentFailure(OutboxMessage message) {
        findLog(message).ifPresent(log -> {
            log.setStatus(IntegrationStatus.FAILED);
            log.setErrorMessage(message.getErrorMessage());
            log.setRetryCount(message.getRetryCount());
            log.setLastAttemptAt(OffsetDateTime.now());
            integrationLogRepository.save(log);
            integrationEventPublisher.publishLogUpdated(log, message);
        });
    }

    private Optional<IntegrationLog> findLog(OutboxMessage message) {
        return integrationLogRepository.findByOutboxMessageId(message.getId())
                .or(() -> {
                    log.debug("[LogSync] No IntegrationLog for outboxMessageId={}", message.getId());
                    return Optional.empty();
                });
    }
}
