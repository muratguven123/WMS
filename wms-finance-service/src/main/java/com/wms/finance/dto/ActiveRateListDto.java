package com.wms.finance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ActiveRateListDto(
        String baseCurrency,
        LocalDate rateDate,
        String rateType,
        List<ExchangeRateDto> rates,
        LocalDateTime lastTcmbSyncAt
) {}
