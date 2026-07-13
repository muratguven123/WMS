package com.wms.finance.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ContractDto(
        Long id,
        Long customerId,
        Long currencyId,
        String currencyCode,
        String contractCode,
        LocalDateTime startDate,
        LocalDateTime endDate
) {}
