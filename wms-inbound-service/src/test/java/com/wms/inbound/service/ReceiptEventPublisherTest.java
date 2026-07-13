package com.wms.inbound.service;

import com.wms.inbound.dto.ReceiptApprovedEvent;
import com.wms.inbound.dto.ReceiptItemEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ReceiptEventPublisher publisher;

    private static final String TOPIC = "wms.inbound.receipts";

    @BeforeEach
    void setUp() {
        publisher = new ReceiptEventPublisher(kafkaTemplate, TOPIC);
    }

    @Test
    void publishReceiptApproved_sendsToConfiguredTopic() {
        Long receiptId = 1L;
        ReceiptApprovedEvent event = new ReceiptApprovedEvent(
                receiptId,
                1L,
                1L,
                1L,
                Instant.parse("2026-07-04T12:00:00Z"),
                List.of(new ReceiptItemEventDto("PROD_001", new BigDecimal("5.0000"), "LOT1", "SN1", null))
        );

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(null, metadata);

        when(kafkaTemplate.send(eq(TOPIC), eq(receiptId.toString()), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publishReceiptApproved(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eq(event));

        assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo(receiptId.toString());
    }
}
