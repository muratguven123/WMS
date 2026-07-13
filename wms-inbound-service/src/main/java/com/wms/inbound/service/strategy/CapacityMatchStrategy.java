package com.wms.inbound.service.strategy;

import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderItem;
import com.wms.inbound.entity.ReceiptItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CapacityMatchStrategy implements PutawayStrategy {

    @Override
    public Optional<Long> findPutawayLocation(ReceiptItem item, Long warehouseLocationId) {
        throw new UnsupportedOperationException("Use PutawayEngineService for orchestration");
    }

    @Override
    public List<StorageLocationResponse> filterLocations(
            List<StorageLocationResponse> candidates, ReceiptItem item) {

        BigDecimal quantity = item.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid receipt item quantity: {}", quantity);
            return List.of();
        }

        BigDecimal unitVolume = BigDecimal.ZERO;
        BigDecimal unitWeight = BigDecimal.ZERO;

        if (item.getReceipt() != null && item.getReceipt().getInboundOrder() != null) {
            InboundOrder inboundOrder = item.getReceipt().getInboundOrder();
            InboundOrderItem orderItem = inboundOrder.getItems().stream()
                    .filter(oi -> oi.getProductCode().equals(item.getProductCode()))
                    .findFirst()
                    .orElse(null);

            if (orderItem != null) {
                unitVolume = orderItem.getUnitVolume() != null ? orderItem.getUnitVolume() : BigDecimal.ZERO;
                unitWeight = orderItem.getUnitWeight() != null ? orderItem.getUnitWeight() : BigDecimal.ZERO;
            }
        }

        BigDecimal requiredVolume = unitVolume.multiply(quantity);
        BigDecimal requiredWeight = unitWeight.multiply(quantity);

        log.info("Checking capacity for product {} (qty: {}). Required volume: {}, Required weight: {}",
                item.getProductCode(), quantity, requiredVolume, requiredWeight);

        return candidates.stream()
                .filter(loc -> {
                    BigDecimal remainingVolume = loc.maxVolume().subtract(loc.currentVolume());
                    BigDecimal remainingWeight = loc.maxWeight().subtract(loc.currentWeight());
                    return remainingVolume.compareTo(requiredVolume) >= 0
                            && remainingWeight.compareTo(requiredWeight) >= 0;
                })
                .sorted(Comparator.comparing(StorageLocationResponse::volumeUtilizationPercent)
                        .thenComparing((loc1, loc2) -> {
                            BigDecimal rem1 = loc1.maxVolume().subtract(loc1.currentVolume());
                            BigDecimal rem2 = loc2.maxVolume().subtract(loc2.currentVolume());
                            return rem2.compareTo(rem1);
                        }))
                .collect(Collectors.toList());
    }
}
