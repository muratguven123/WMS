package com.wms.inbound.service.strategy;

import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.entity.ReceiptItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ZoneMatchStrategy implements PutawayStrategy {

    @Override
    public Optional<Long> findPutawayLocation(ReceiptItem item, Long warehouseLocationId) {
        throw new UnsupportedOperationException("Use PutawayEngineService for orchestration");
    }

    @Override
    public List<StorageLocationResponse> filterLocations(
            List<StorageLocationResponse> candidates, ReceiptItem item) {

        String requiredZoneType = ZoneMatchSupport.determineRequiredZoneType(item.getProductCode());
        log.info("Matching product {} with zone type {}", item.getProductCode(), requiredZoneType);
        return ZoneMatchSupport.filterByZone(candidates, item);
    }
}
