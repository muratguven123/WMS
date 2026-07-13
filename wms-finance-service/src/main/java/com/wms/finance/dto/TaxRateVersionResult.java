package com.wms.finance.dto;

public record TaxRateVersionResult(
        TaxRateResponse expiredRate,
        TaxRateResponse newRate
) {}
