package com.wms.core.dto;

import com.wms.core.entity.enums.StorageLocationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/locations/{locationId}/status istek gövdesi.
 * Yalnızca ACTIVE ve BLOCKED manuel olarak set edilebilir.
 */
public record StorageLocationStatusUpdateRequest(
        @NotNull(message = "status is required")
        StorageLocationStatus status
) {
}
