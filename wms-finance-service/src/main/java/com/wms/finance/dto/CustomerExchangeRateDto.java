package com.wms.finance.dto;

import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record CustomerExchangeRateDto(
        Long id,
        Long customerId,
        String sourceCurrency,
        String targetCurrency,
        LocalDate rateDate,
        RateType rateType,
        BigDecimal rate,
        AuditActionType action,
        BigDecimal previousRate
) {}
