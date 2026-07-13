package com.wms.outbound.dto;

import com.wms.outbound.entity.enums.PickingItemStatus;
import com.wms.outbound.entity.enums.PickingListStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PickingListResponse(
        Long id,
        Long warehouseLocationId,
        Long companyId,
        Long createdByUserId,
        Long assignedUserId,
        LocalDateTime assignedAt,
        PickingListStatus status,
        LocalDateTime createdAt,
        List<PickingItemResponse> items
) {
    public record PickingItemResponse(
            Long id,
            Long outboundOrderItemId,
            String productCode,
            Long sourceLocationId,
            String addressCode,
            BigDecimal quantityToPick,
            BigDecimal pickedQuantity,
            PickingItemStatus status
    ) {
    }
}
