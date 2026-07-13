package com.wms.inbound.service.strategy;

import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.entity.ReceiptItem;

import java.util.List;
import java.util.Optional;

public interface PutawayStrategy {
    
    Optional<Long> findPutawayLocation(ReceiptItem item, Long warehouseLocationId);

    /**
     * Filters and sorts candidate locations based on this strategy's rules.
     */
    List<StorageLocationResponse> filterLocations(List<StorageLocationResponse> candidates, ReceiptItem item);
}
