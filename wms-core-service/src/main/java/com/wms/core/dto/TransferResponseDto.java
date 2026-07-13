package com.wms.core.dto;

import java.time.OffsetDateTime;

/**
 * Transfer işlemi sonuç DTO'su.
 */
public record TransferResponseDto(
        Long transferId,
        Long sourceLocationId,
        Long targetLocationId,
        String sku,
        int quantity,
        String status,
        OffsetDateTime createdAt
) {
}
