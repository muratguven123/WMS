package com.wms.outbound.integration;

import com.wms.outbound.dto.AllocateStockRequest;
import com.wms.outbound.dto.AllocatedStockDto;
import com.wms.outbound.dto.InventoryIssueRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class InventoryServiceClient {

    private final WebClient inventoryWebClient;

    public InventoryServiceClient(@Qualifier("inventoryWebClient") WebClient inventoryWebClient) {
        this.inventoryWebClient = inventoryWebClient;
    }

    public List<AllocatedStockDto> allocateStock(AllocateStockRequest request) {
        log.info("Requesting stock allocation from inventory service: {}", request);
        try {
            List<AllocatedStockDto> response = inventoryWebClient.post()
                    .uri("/api/inventory/allocate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<AllocatedStockDto>>() {})
                    .block();

            return response != null ? response : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to allocate stock for product: {}", request.productCode(), e);
            throw new RuntimeException("Inventory service communication error: " + e.getMessage(), e);
        }
    }

    public void issueStock(InventoryIssueRequest request) {
        log.info("Requesting stock issue from inventory service for shipment: {}", request.getShipmentNumber());
        try {
            inventoryWebClient.post()
                    .uri("/api/inventory/issue")
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.error("Failed to issue stock for shipment: {}", request.getShipmentNumber(), e);
            throw new RuntimeException("Inventory service communication error: " + e.getMessage(), e);
        }
    }
}
