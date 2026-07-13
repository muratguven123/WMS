package com.wms.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AllocateStockRequest(
        @NotBlank String productCode,
        @NotNull @Positive BigDecimal requiredQuantity,
        @NotBlank String allocationStrategy,
        @NotNull Long warehouseLocationId
) {
}
