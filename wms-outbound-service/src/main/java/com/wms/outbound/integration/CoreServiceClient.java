package com.wms.outbound.integration;

import com.wms.outbound.dto.StorageLocationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;


@Slf4j
@Component
public class CoreServiceClient {

    private final WebClient coreWebClient;

    public CoreServiceClient(@Qualifier("coreWebClient") WebClient coreWebClient) {
        this.coreWebClient = coreWebClient;
    }

    public StorageLocationResponse getStorageLocation(Long locationId) {
        log.info("Fetching storage location details for id: {}", locationId);
        try {
            return coreWebClient.get()
                    .uri("/api/locations/{locationId}", locationId)
                    .retrieve()
                    .bodyToMono(StorageLocationResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch storage location details for id: {}", locationId, e);
            throw new RuntimeException("Core service communication error: " + e.getMessage(), e);
        }
    }
}
