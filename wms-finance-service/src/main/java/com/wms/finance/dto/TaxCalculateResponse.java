package com.wms.finance.dto;

import java.math.BigDecimal;

public record TaxCalculateResponse(
        BigDecimal net,
        BigDecimal tax,
        BigDecimal gross,
        String taxTypeCode,
        BigDecimal rate,
        boolean inclusive
) {}
