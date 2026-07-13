package com.wms.inventory.messaging;

import com.wms.inventory.service.InboundReceiptInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ReceiptApprovedConsumer {

    private final InboundReceiptInventoryService inboundReceiptInventoryService;

    @KafkaListener(
            topics = "${wms.kafka.topics.receipt-approved:wms.inbound.receipts}",
            groupId = "${wms.kafka.consumer.group-id:wms-inventory-service}"
    )
    public void consume(ReceiptApprovedEvent event) {
        log.info("Received ReceiptApprovedEvent from Kafka: receiptId={}", event.receiptId());
        inboundReceiptInventoryService.applyReceiptApproved(event);
    }
}
