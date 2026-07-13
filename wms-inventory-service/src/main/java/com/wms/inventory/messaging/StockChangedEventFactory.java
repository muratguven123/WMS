package com.wms.inventory.messaging;

import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StockChangedEventFactory {

    private final DomainEventPublisher domainEventPublisher;

    public void publish(Long companyId,
                        Long warehouseLocationId,
                        Long binId,
                        String productCode,
                        BigDecimal qty,
                        BigDecimal delta,
                        String transactionType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("binId", binId != null ? binId.toString() : null);
        payload.put("skuId", productCode);
        payload.put("productCode", productCode);
        payload.put("qty", qty);
        payload.put("delta", delta);
        payload.put("transactionType", transactionType);

        DomainEvent event = DomainEvent.of(
                EventType.STOCK_CHANGED,
                companyId,
                warehouseLocationId,
                payload);
        domainEventPublisher.publish(KafkaTopics.STOCK_CHANGED, event);
    }
}
