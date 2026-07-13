package com.wms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryIssueLine(
        @NotBlank String productCode,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity
) {}
