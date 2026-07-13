package com.wms.finance.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record OrderResponseDto(
        Long orderId,
        Long customerId,
        Long currencyId,
        String currencyCode,
        BigDecimal amount,
        Instant orderDate
) {}
