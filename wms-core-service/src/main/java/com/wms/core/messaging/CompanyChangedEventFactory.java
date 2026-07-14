package com.wms.core.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Firma CRUD sonrası Kafka → STOMP ({@code /topic/org.companies}) yayını.
 */
@Component
@RequiredArgsConstructor
public class CompanyChangedEventFactory {

    private final DomainEventPublisher domainEventPublisher;

    public void publish(Long organizationId, Long companyId, String action, String name, boolean active) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("organizationId", organizationId.toString());
        payload.put("companyId", companyId.toString());
        payload.put("action", action);
        payload.put("name", name);
        payload.put("active", String.valueOf(active));

        DomainEvent event = DomainEvent.of(EventType.COMPANY_CHANGED, companyId, null, payload);
        domainEventPublisher.publish(KafkaTopics.COMPANY_CHANGED, event);
    }
}
