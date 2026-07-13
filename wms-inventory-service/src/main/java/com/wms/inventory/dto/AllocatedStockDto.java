package com.wms.inventory.dto;

import java.math.BigDecimal;

public record AllocatedStockDto(
        Long storageLocationId,
        String lotNumber,
        BigDecimal allocatedQuantity
) {}
