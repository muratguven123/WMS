package com.wms.core.messaging;

import com.wms.core.entity.StorageLocation;
import com.wms.core.entity.enums.StorageLocationStatus;
import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LocationUpdatedEventFactory {

    private final DomainEventPublisher domainEventPublisher;

    public void publish(StorageLocation location) {
        Long warehouseLocationId = location.getZone().getLocation().getId();
        Long companyId = location.getZone().getLocation().getCompany().getId();

        BigDecimal utilizationPct = BigDecimal.ZERO;
        if (location.getMaxVolume() != null
                && location.getMaxVolume().compareTo(BigDecimal.ZERO) > 0) {
            utilizationPct = location.getCurrentVolume()
                    .divide(location.getMaxVolume(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("locationId", location.getId().toString());
        payload.put("storageLocationId", location.getId().toString());
        payload.put("status", location.getStatus().name());
        payload.put("utilizationPct", utilizationPct);
        payload.put("blocked", location.getStatus() == StorageLocationStatus.BLOCKED);
        payload.put("addressCode", location.getAddressCode());

        DomainEvent event = DomainEvent.of(
                EventType.LOCATION_UPDATED,
                companyId,
                warehouseLocationId,
                payload);
        domainEventPublisher.publish(KafkaTopics.LOCATION_UPDATED, event);
    }
}
