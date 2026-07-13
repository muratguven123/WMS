package com.wms.finance.integration.tcmb;

import com.wms.finance.entity.enums.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TCMB XML'inden parse edilen tek bir kur kaydını temsil eder.
 *
 * @param currencyCode Döviz kodu (USD, EUR, GBP …)
 * @param rateDate     Kur tarihi (XML'deki Tarih_Date/@Date alanı)
 * @param rateType     Kur türü (BUYING, SELLING, EFFECTIVE_BUYING, EFFECTIVE_SELLING)
 * @param rate         Kur değeri
 */
public record TcmbParsedRate(
        String currencyCode,
        LocalDate rateDate,
        RateType rateType,
        BigDecimal rate
) {}
