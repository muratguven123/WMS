package com.wms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxCalculateRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String taxTypeCode,
        @NotBlank String mode,
        @NotNull Long countryId,
        Long locationId,
        Long customerId,
        String productType,
        String operationType,
        LocalDate transactionDate
) {}
