package com.wms.inventory.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

@Slf4j
@Component
public class CoreServiceClient {

    private final WebClient coreWebClient;

    public CoreServiceClient(@Qualifier("coreWebClient") WebClient coreWebClient) {
        this.coreWebClient = coreWebClient;
    }

    /**
     * Checks if a storage location has enough available capacity.
     */
    public boolean checkLocationCapacity(Long storageLocationId, BigDecimal volume, BigDecimal weight) {
        try {
            log.info("Checking capacity for storage location {} (volume={}, weight={})", storageLocationId, volume, weight);
            Boolean result = coreWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/locations/{locationId}/capacity/check")
                            .queryParam("volume", volume)
                            .queryParam("weight", weight)
                            .build(storageLocationId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            return result != null && result;
        } catch (Exception e) {
            log.error("Failed to check location capacity for location {}", storageLocationId, e);
            throw new RuntimeException("Core service connection failed during capacity check", e);
        }
    }

    /**
     * Updates storage location load on capacity service.
     */
    public void updateLocationLoad(Long storageLocationId, BigDecimal volumeDelta, BigDecimal weightDelta, boolean isAddition) {
        try {
            log.info("Updating load for storage location {} (volumeDelta={}, weightDelta={}, isAddition={})",
                    storageLocationId, volumeDelta, weightDelta, isAddition);
            coreWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/locations/{locationId}/capacity/load")
                            .queryParam("volumeDelta", volumeDelta)
                            .queryParam("weightDelta", weightDelta)
                            .queryParam("isAddition", isAddition)
                            .build(storageLocationId))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.error("Failed to update location load for location {}", storageLocationId, e);
            throw new RuntimeException("Core service connection failed during capacity load update", e);
        }
    }
}
