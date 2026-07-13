package com.wms.inbound.scheduler;

import com.wms.inbound.entity.OutboxMessage;
import com.wms.inbound.entity.enums.OutboxStatus;
import com.wms.inbound.repository.OutboxMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class OutboxWorker {

    private final OutboxMessageRepository outboxMessageRepository;
    private final WebClient integrationWebClient;

    public OutboxWorker(OutboxMessageRepository outboxMessageRepository,
                        @Qualifier("integrationWebClient") WebClient integrationWebClient) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.integrationWebClient = integrationWebClient;
    }

    /**
     * Periodically checks the database for PENDING outbox messages
     * and attempts to publish them to the integration service.
     */
    @Scheduled(fixedDelayString = "${outbox.worker.fixed-delay-ms:5000}")
    public void processPendingMessages() {
        List<OutboxMessage> pendingMessages = outboxMessageRepository.findByStatus(OutboxStatus.PENDING);
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox messages to process", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            try {
                log.info("Publishing outbox message ID: {} for aggregate: {} - {}",
                        message.getId(), message.getAggregateType(), message.getAggregateId());

                // Call wms-integration-service to notify ERP
                integrationWebClient.post()
                        .uri("/api/integration/erp/receipts")
                        .bodyValue(message.getPayload())
                        .retrieve()
                        .toBodilessEntity()
                        .block();

                // On success, mark as PROCESSED
                message.setStatus(OutboxStatus.PROCESSED);
                message.setProcessedAt(LocalDateTime.now());
                message.setErrorMessage(null);
                outboxMessageRepository.save(message);
                log.info("Successfully processed outbox message ID: {}", message.getId());

            } catch (Exception e) {
                log.error("Failed to process outbox message ID: {}. Error: {}", message.getId(), e.getMessage());
                message.setStatus(OutboxStatus.FAILED);
                message.setErrorMessage(e.getMessage());
                outboxMessageRepository.save(message);
            }
        }
    }
}
