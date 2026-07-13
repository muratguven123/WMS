package com.wms.core.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LocationProvisionedEventFactory {

    private final DomainEventPublisher domainEventPublisher;

    public void publish(Long companyId,
                        Long locationId,
                        Long countryId,
                        Long regionId,
                        String timezone) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("locationId", locationId);
        payload.put("companyId", companyId);
        payload.put("countryId", countryId);
        payload.put("regionId", regionId);
        payload.put("timezone", timezone);

        DomainEvent event = DomainEvent.of(
                EventType.LOCATION_PROVISIONED,
                companyId,
                locationId,
                payload);
        domainEventPublisher.publish(KafkaTopics.LOCATION_PROVISIONED, event);
    }
}
