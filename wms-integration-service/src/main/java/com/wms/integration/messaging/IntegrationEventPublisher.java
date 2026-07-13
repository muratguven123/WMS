package com.wms.integration.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.events.DomainEvent;
import com.wms.events.EventType;
import com.wms.events.KafkaTopics;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.IntegrationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class IntegrationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishLogUpdated(IntegrationLog integrationLog, OutboxMessage message) {
        Long companyId = extractCompanyId(message.getPayload());
        Long locationId = message.getLocationId();
        if (companyId == null) {
            log.debug("Skipping integration event publish — companyId not found in payload");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("logId", integrationLog.getId().toString());
        payload.put("system", integrationLog.getLocationIntegrationConfig().getIntegrationSystem().getCode());
        payload.put("status", integrationLog.getStatus().name());
        payload.put("direction", "OUTBOUND");
        payload.put("errorMessage", integrationLog.getErrorMessage());
        payload.put("jobCode", message.getJobCode());
        payload.put("retryCount", integrationLog.getRetryCount());

        DomainEvent event = DomainEvent.of(EventType.INTEGRATION_LOG_UPDATED, companyId, locationId, payload);
        String key = companyId + ":" + locationId;
        kafkaTemplate.send(KafkaTopics.INTEGRATION_LOG_UPDATED, key, event);
    }

    private Long extractCompanyId(String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            if (node.hasNonNull("companyId")) {
                return Long.parseLong(node.get("companyId").asText());
            }
            if (node.hasNonNull("warehouseLocationId")) {
                return Long.parseLong(node.get("warehouseLocationId").asText());
            }
        } catch (Exception ex) {
            log.debug("Could not parse companyId from outbox payload: {}", ex.getMessage());
        }
        return null;
    }
}
