package com.wms.finance.dto;

import com.wms.finance.entity.enums.RateType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record ContractFixedRateDto(
        Long id,
        Long contractId,
        Long sourceCurrencyId,
        String sourceCurrencyCode,
        Long targetCurrencyId,
        String targetCurrencyCode,
        BigDecimal rate,
        RateType rateType,
        LocalDate validFrom,
        LocalDate validTo,
        boolean active
) {}
