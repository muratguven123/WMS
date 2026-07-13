package com.wms.inbound.service;

import com.wms.inbound.dto.StorageLocationResponse;
import com.wms.inbound.entity.ReceiptItem;
import com.wms.inbound.integration.CoreServiceClient;
import com.wms.inbound.service.strategy.CapacityMatchStrategy;
import com.wms.inbound.service.strategy.ZoneMatchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PutawayEngineService {

    private final CoreServiceClient coreServiceClient;
    private final ZoneMatchStrategy zoneMatchStrategy;
    private final CapacityMatchStrategy capacityMatchStrategy;

    /**
     * Finds the best putaway location for a given receipt item in the specified warehouse.
     * Uses ZoneMatchStrategy first, then filters candidates with CapacityMatchStrategy.
     */
    public Optional<Long> findPutawayLocation(ReceiptItem item, Long warehouseLocationId) {
        return findRecommendedLocation(item, warehouseLocationId).map(StorageLocationResponse::id);
    }

    /**
     * Tam öneri detayıyla (adres kodu, doluluk) en uygun gözü döner.
     */
    public Optional<StorageLocationResponse> findRecommendedLocation(
            ReceiptItem item, Long warehouseLocationId) {
        log.info("Starting putaway calculation for item: productCode={}, quantity={}, warehouseLocationId={}",
                item.getProductCode(), item.getQuantity(), warehouseLocationId);

        // 1. Fetch active candidate locations from core service
        List<StorageLocationResponse> allLocations = coreServiceClient.getActiveLocations();
        
        // 2. Filter locations belonging to the target warehouse/location
        List<StorageLocationResponse> warehouseLocations = allLocations.stream()
                .filter(loc -> warehouseLocationId.equals(loc.locationId()))
                .collect(Collectors.toList());

        if (warehouseLocations.isEmpty()) {
            log.warn("No active storage locations found in warehouse {}", warehouseLocationId);
            return Optional.empty();
        }

        // 3. Apply Zone Matching Strategy
        List<StorageLocationResponse> zoneMatched = zoneMatchStrategy.filterLocations(warehouseLocations, item);
        if (zoneMatched.isEmpty()) {
            log.warn("No storage locations matched zone criteria for product {}", item.getProductCode());
            return Optional.empty();
        }

        // 4. Apply Capacity Matching Strategy on the zone-matched candidates
        List<StorageLocationResponse> capacityMatched = capacityMatchStrategy.filterLocations(zoneMatched, item);
        if (capacityMatched.isEmpty()) {
            log.warn("No storage locations matched capacity criteria in the selected zones for product {}", item.getProductCode());
            return Optional.empty();
        }

        // 5. Return the top recommendation (the first location in the sorted candidate list)
        StorageLocationResponse recommendedLocation = capacityMatched.get(0);
        log.info("Recommended putaway location for product {}: {} (id: {}, utilization: {}%)",
                item.getProductCode(), recommendedLocation.addressCode(), recommendedLocation.id(),
                recommendedLocation.volumeUtilizationPercent());

        return Optional.of(recommendedLocation);
    }
}
