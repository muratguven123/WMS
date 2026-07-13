package com.wms.integration.outbox;

import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.OutboxStatus;
import com.wms.integration.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Transactional Outbox Worker — bekleyen mesajları ERP'ye gönderir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationOutboxWorker {

    private static final int BATCH_SIZE = 50;

    private final OutboxMessageRepository outboxMessageRepository;
    private final OutboxMessageProcessor outboxMessageProcessor;

    @Scheduled(fixedDelayString = "${outbox.worker.fixed-delay-ms:5000}")
    @Transactional
    public void processOutbox() {
        List<OutboxMessage> batch = outboxMessageRepository.findPendingWithLock(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                OffsetDateTime.now(),
                PageRequest.of(0, BATCH_SIZE)
        );

        if (batch.isEmpty()) {
            return;
        }

        log.info("[OutboxWorker] Processing {} messages", batch.size());

        for (Long messageId : batch.stream().map(OutboxMessage::getId).toList()) {
            outboxMessageProcessor.process(messageId);
        }
    }
}
