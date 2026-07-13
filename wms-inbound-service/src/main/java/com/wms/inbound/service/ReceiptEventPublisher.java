package com.wms.inbound.service;

import com.wms.inbound.dto.ReceiptApprovedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ReceiptEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String receiptApprovedTopic;

    public ReceiptEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${wms.kafka.topics.receipt-approved:wms.inbound.receipts}") String receiptApprovedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.receiptApprovedTopic = receiptApprovedTopic;
    }

    public void publishReceiptApproved(ReceiptApprovedEvent event) {
        log.info("Publishing ReceiptApprovedEvent to Kafka topic '{}'. Receipt ID: {}",
                receiptApprovedTopic, event.receiptId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                receiptApprovedTopic,
                event.receiptId().toString(),
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published ReceiptApprovedEvent to Kafka. Receipt ID: {}, topic: {}, partition: {}, offset: {}",
                        event.receiptId(),
                        receiptApprovedTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish ReceiptApprovedEvent to Kafka. Receipt ID: {}, topic: {}",
                        event.receiptId(), receiptApprovedTopic, ex);
            }
        });
    }
}
