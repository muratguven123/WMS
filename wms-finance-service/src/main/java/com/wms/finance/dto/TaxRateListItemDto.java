package com.wms.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaxRateListItemDto(
        Long id,
        String taxTypeCode,
        Long countryId,
        Long locationId,
        Long customerId,
        String productType,
        String operationType,
        BigDecimal rate,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        LocalDateTime createdAt
) {}
