package com.wms.integration.api.dto;

import com.wms.integration.entity.enums.IntegrationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

/**
 * GET /api/integrations/logs yanıt DTO'su.
 *
 * <p>Payload alanları (requestPayload, responsePayload) opsiyonel olarak
 * döndürülür; büyük TEXT içeriklerini her seferinde taşımamak için
 * listeleme sorgusunda {@code includePayloads=false} (default) kullanılır.
 * Detay endpoint'i bunları her zaman döner.
 */
@Value
@Builder
public class IntegrationLogResponse {

    Long             id;
    Long             locationId;
    String           locationName;      // LocationIntegrationConfig → IntegrationSystem.name
    String           erpSystemCode;     // IntegrationSystem.code
    String           jobCode;           // IntegrationJob.code
    String           jobName;           // IntegrationJob.name
    IntegrationStatus status;
    int              retryCount;
    String           externalReference;
    String           errorMessage;
    OffsetDateTime   createdAt;
    OffsetDateTime   lastAttemptAt;
    Long             outboxMessageId;

    /** Yalnızca detay/retry endpoint veya includePayloads=true sorgulanında dolar. */
    String           requestPayload;
    String           responsePayload;
}
