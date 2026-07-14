package com.wms.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeDifferenceTaxRequest(
        @NotNull @Positive BigDecimal exchangeDifferenceAmount,
        @NotBlank String taxTypeCode,
        @NotNull LocalDate date,
        @NotNull Long locationId,
        Long countryId
) {}
