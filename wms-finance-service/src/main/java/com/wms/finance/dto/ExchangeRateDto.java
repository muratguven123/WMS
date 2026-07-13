package com.wms.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Müşteriye özgü kur sorgusu sonucu.
 *
 * @param sourceCurrency kaynak para birimi kodu (örn. USD)
 * @param targetCurrency hedef para birimi kodu (örn. TRY)
 * @param rate           kur değeri
 * @param rateDate       kurun ait olduğu tarih
 * @param rateType       kullanılan kur tipi (BUYING, SELLING vb.)
 * @param rateSource     kur kaynağı (TCMB, MANUAL vb.)
 * @param fallbackUsed   istenilen tarihte kur bulunamadığında geçmiş kur kullanıldıysa {@code true}
 */
public record ExchangeRateDto(
        String sourceCurrency,
        String targetCurrency,
        BigDecimal rate,
        LocalDate rateDate,
        String rateType,
        String rateSource,
        boolean fallbackUsed
) {}
