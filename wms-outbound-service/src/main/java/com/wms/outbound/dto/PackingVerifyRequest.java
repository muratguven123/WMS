package com.wms.outbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PackingVerifyRequest(
        @NotNull Long pickingListId,
        @NotBlank String productBarcode,
        @NotNull @Positive BigDecimal scannedQuantity
) {
}
