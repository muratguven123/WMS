package com.wms.notification.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class KafkaEventBridge {

    private final StompMessageBroadcaster stompMessageBroadcaster;

    @KafkaListener(topics = {
            KafkaTopics.STOCK_CHANGED,
            KafkaTopics.LOCATION_UPDATED,
            KafkaTopics.APPROVAL_CREATED,
            KafkaTopics.APPROVAL_RESOLVED,
            KafkaTopics.INTEGRATION_LOG_UPDATED,
            KafkaTopics.TASK_ASSIGNED,
            KafkaTopics.TASK_PROGRESS,
            KafkaTopics.TASK_COMPLETED,
            KafkaTopics.OPERATOR_STATUS_UPDATED,
            KafkaTopics.COMPANY_CHANGED
    }, groupId = "${spring.kafka.consumer.group-id:wms-notification-service}")
    public void onDomainEvent(DomainEvent event) {
        log.info("Kafka event received: type={} companyId={}", event.eventType(), event.companyId());
        stompMessageBroadcaster.broadcast(event);
    }
}
