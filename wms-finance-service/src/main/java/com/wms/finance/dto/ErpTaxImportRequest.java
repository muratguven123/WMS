package com.wms.finance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ERP'den çekilen vergi oranının finance master data'ya yazımı.
 */
public record ErpTaxImportRequest(
        @NotBlank String taxTypeCode,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal rate,
        @NotNull Long countryId,
        Long locationId,
        LocalDate validFrom,
        LocalDate validTo
) {}
