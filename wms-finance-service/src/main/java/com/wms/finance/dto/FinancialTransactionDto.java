package com.wms.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FinancialTransactionDto(
        Long id,
        Long companyId,
        Long locationId,
        Long contractId,
        String originalCurrencyCode,
        BigDecimal originalAmount,
        BigDecimal exchangeRate,
        String baseCurrencyCode,
        BigDecimal convertedAmount,
        Instant transactionDate,
        boolean fallbackRateUsed
) {}
