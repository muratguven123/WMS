package com.wms.inventory.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory → integration-service COUNT_SYNC enqueue istemcisi.
 */
@Slf4j
@Component
public class IntegrationServiceClient {

    private final WebClient integrationWebClient;

    public IntegrationServiceClient(@Qualifier("integrationWebClient") WebClient integrationWebClient) {
        this.integrationWebClient = integrationWebClient;
    }

    public void enqueueCountResult(
            Long companyId,
            Long locationId,
            Long countId,
            String productCode,
            BigDecimal expectedQty,
            BigDecimal countedQty) {
        Map<String, Object> line = new HashMap<>();
        line.put("productCode", productCode);
        line.put("expectedQty", expectedQty);
        line.put("countedQty", countedQty);
        line.put("difference", countedQty.subtract(expectedQty));

        Map<String, Object> body = new HashMap<>();
        body.put("companyId", companyId);
        body.put("locationId", locationId);
        body.put("countId", countId);
        body.put("countDate", LocalDate.now().toString());
        body.put("lines", List.of(line));

        try {
            integrationWebClient.post()
                    .uri("/api/integrations/counts")
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("COUNT_SYNC enqueued countId={} product={}", countId, productCode);
        } catch (Exception e) {
            log.warn("COUNT_SYNC enqueue failed (non-blocking): {}", e.getMessage());
        }
    }
}
