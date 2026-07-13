package com.wms.finance.dto;

import com.wms.finance.entity.TaxRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaxRateResponse(
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
) {
    public static TaxRateResponse from(TaxRate rate) {
        return new TaxRateResponse(
                rate.getId(),
                rate.getTaxType().getCode(),
                rate.getCountryId(),
                rate.getLocationId(),
                rate.getCustomerId(),
                rate.getProductType(),
                rate.getOperationType(),
                rate.getRate(),
                rate.getStartDate(),
                rate.getEndDate(),
                rate.isActive(),
                rate.getCreatedAt());
    }
}
