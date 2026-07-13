package com.wms.localization.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import com.wms.localization.service.LocationProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class LocationProvisionedConsumer {

    private final LocationProvisioningService locationProvisioningService;

    @KafkaListener(
            topics = KafkaTopics.LOCATION_PROVISIONED,
            groupId = "${wms.kafka.consumer.group-id:wms-localization-service}"
    )
    public void consume(DomainEvent event) {
        if (event.eventType() != EventType.LOCATION_PROVISIONED) {
            return;
        }
        log.info("Received LOCATION_PROVISIONED for locationId={}", event.locationId());
        locationProvisioningService.provisionFromEvent(event);
    }
}
