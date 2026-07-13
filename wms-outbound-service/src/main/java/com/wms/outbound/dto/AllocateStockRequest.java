package com.wms.outbound.dto;

import java.math.BigDecimal;

public record AllocateStockRequest(
        String productCode,
        BigDecimal requiredQuantity,
        String allocationStrategy,
        Long warehouseLocationId
) {
}
