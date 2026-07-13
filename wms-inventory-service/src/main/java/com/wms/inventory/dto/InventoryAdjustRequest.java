package com.wms.inventory.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryAdjustRequest(
        @NotNull Long storageLocationId,
        @NotNull String productCode,
        String lotNumber,
        String serialNumber,
        @NotNull BigDecimal actualQuantity,
        @NotNull Long companyId,
        @NotNull Long warehouseLocationId
) {}
