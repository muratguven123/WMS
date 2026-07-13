package com.wms.integration.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

/**
 * POST /api/integrations/logs/{logId}/retry yanıt DTO'su.
 */
@Value
@Builder
public class RetryResponse {

    Long            logId;
    Long            outboxMessageId;
    String          message;
    OffsetDateTime  scheduledAt;      // OutboxMessage.nextAttemptAt (= now)
}
