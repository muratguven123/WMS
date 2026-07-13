package com.wms.outbound.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ConfirmPickRequest(
        @NotNull BigDecimal pickedQty
) {
}
