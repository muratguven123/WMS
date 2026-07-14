package com.wms.billing.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Finance vergi motoru istemci sözleşmesi.
 */
public interface TaxLookupService {

    TaxLineResult calculateLineTax(
            BigDecimal amount,
            String taxTypeCode,
            Long countryId,
            Long locationId,
            Long customerId,
            String productType,
            String operationType,
            LocalDate transactionDate);

    TaxLineResult calculateExchangeDifferenceTax(
            BigDecimal exchangeDifferenceAmount,
            String taxTypeCode,
            LocalDate date,
            Long locationId,
            Long countryId);

    record TaxLineResult(
            BigDecimal net,
            BigDecimal tax,
            BigDecimal gross,
            String taxTypeCode,
            BigDecimal rate,
            boolean inclusive
    ) {}
}
