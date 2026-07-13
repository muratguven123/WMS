package com.wms.inventory.messaging;

import com.wms.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, DomainEvent event) {
        String key = partitionKey(event);
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} to {}: {}", event.eventType(), topic, ex.getMessage());
                    } else {
                        log.debug("Published {} to topic {}", event.eventType(), topic);
                    }
                });
    }

    private String partitionKey(DomainEvent event) {
        String location = event.locationId() != null ? event.locationId().toString() : "global";
        return event.companyId() + ":" + location;
    }
}
