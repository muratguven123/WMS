package com.wms.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionResultDto(
        BigDecimal originalAmount,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal exchangeRate,
        BigDecimal convertedAmount,
        LocalDate rateDateUsed,
        boolean fallbackRateUsed
) {}
