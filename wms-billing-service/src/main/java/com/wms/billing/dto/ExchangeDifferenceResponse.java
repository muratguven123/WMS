package com.wms.billing.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ExchangeDifferenceResponse(
        Long id,
        Long invoiceId,
        LocalDateTime calculationDate,
        BigDecimal originalPaidAmount,
        BigDecimal rateAtPayment,
        BigDecimal exchangeDifferenceAmount,
        String actionTaken,
        BigDecimal taxAmount,
        String taxTypeCode
) {}
