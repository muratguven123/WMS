package com.wms.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record OrderRequestDto(
        @NotNull Long customerId,
        @NotNull Long currencyId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull Instant orderDate,
        Long contractId
) {}
