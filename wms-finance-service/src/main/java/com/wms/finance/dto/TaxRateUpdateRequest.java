package com.wms.finance.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxRateUpdateRequest(

        @NotNull(message = "taxRateId is required")
        Long taxRateId,

        @NotNull(message = "newRate is required")
        @DecimalMin(value = "0.00", message = "Rate cannot be negative")
        @DecimalMax(value = "100.00", message = "Rate cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "Rate must have at most 3 integer and 2 decimal digits")
        BigDecimal newRate,

        @NotNull(message = "effectiveDate is required")
        @FutureOrPresent(message = "effectiveDate cannot be in the past")
        LocalDate effectiveDate
) {}
