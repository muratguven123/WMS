package com.wms.core.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


/**
 * Depolar arası stok transfer isteği DTO'su.
 */
public record TransferRequestDto(

        @NotNull(message = "Source location ID is required")
        Long sourceLocationId,

        @NotNull(message = "Target location ID is required")
        Long targetLocationId,

        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        String notes
) {
}
