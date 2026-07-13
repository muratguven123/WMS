package com.wms.outbound.dto;

import java.math.BigDecimal;

public record AllocatedStockDto(
        Long storageLocationId,
        String lotNumber,
        BigDecimal allocatedQuantity
) {
}
