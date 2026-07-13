package com.wms.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InternalMoveRequest(
        @NotNull Long sourceLocationId,
        @NotNull Long targetLocationId,
        @NotNull String productCode,
        @NotNull @Positive BigDecimal quantity,
        String lotNumber
) {}
