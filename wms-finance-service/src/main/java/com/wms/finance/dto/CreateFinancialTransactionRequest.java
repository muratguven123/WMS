package com.wms.finance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record CreateFinancialTransactionRequest(
        @NotNull Long companyId,
        @NotNull Long locationId,
        Long customerId,
        Long contractId,
        @NotNull BigDecimal amount,
        @NotNull Instant transactionDate,
        String rateType
) {}
