package com.wms.integration.adapter.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ERP'den çekilen döviz kuru.
 */
@Value
@Builder
public class ExchangeRateDto {

    /** Kaynak para birimi (USD, EUR vb.) */
    String fromCurrency;

    /** Hedef para birimi (TRY vb.) */
    String toCurrency;

    /** Kur değeri */
    BigDecimal rate;

    /** Kur tarihi */
    LocalDate rateDate;
}
