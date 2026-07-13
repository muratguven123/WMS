package com.wms.billing.dto;

import com.wms.billing.domain.enums.InvoiceStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long customerId,
        Long locationId,
        LocalDateTime issueDate,
        LocalDateTime createdAt,
        String invoiceCurrency,
        String accountingCurrency,
        LocalDate exchangeRateDate,
        BigDecimal exchangeRateValue,
        List<InvoiceItemResultDto> items,
        BigDecimal subtotalOriginal,
        BigDecimal taxAmountOriginal,
        BigDecimal grandTotalOriginal,
        BigDecimal grandTotalAccounting,
        InvoiceStatus status,
        boolean preview
) {}
