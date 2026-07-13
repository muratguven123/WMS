package com.wms.inbound.integration;

import com.wms.inbound.dto.CustomPageResponse;
import com.wms.inbound.dto.StorageLocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class CoreServiceClient {

    private final WebClient coreWebClient;

    public CoreServiceClient(@Qualifier("coreWebClient") WebClient coreWebClient) {
        this.coreWebClient = coreWebClient;
    }

    /**
     * Fetches all active storage locations from the core service.
     */
    public List<StorageLocationResponse> getActiveLocations() {
        try {
            log.info("Fetching active storage locations from core service");
            Mono<CustomPageResponse<StorageLocationResponse>> responseMono = coreWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/locations/search")
                            .queryParam("status", "ACTIVE")
                            .queryParam("isActive", true)
                            .queryParam("size", 1000)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CustomPageResponse<StorageLocationResponse>>() {});

            CustomPageResponse<StorageLocationResponse> response = responseMono.block();
            if (response != null && response.content() != null) {
                log.info("Successfully fetched {} active locations", response.content().size());
                return response.content();
            }
        } catch (Exception e) {
            log.error("Failed to fetch active storage locations from core service", e);
        }
        return Collections.emptyList();
    }
}
