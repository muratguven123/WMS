package com.wms.core.dto.audit;

import java.time.OffsetDateTime;

public record AuditLogEntryDto(
        Long id,
        String entityName,
        Long entityId,
        String actionType,
        String fieldName,
        String oldValue,
        String newValue,
        Long changedByUserId,
        OffsetDateTime changedAt
) {}
