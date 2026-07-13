package com.wms.billing.service;

import com.wms.billing.domain.enums.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Döviz kuru çözümleme port interface'i.
 *
 * <p>Implementasyon, döviz kurlarını harici bir kaynaktan (merkez bankası API'si,
 * wms-finance-service, DB tablosu vb.) sağlar. Hesaplama motoru sadece bu interface'e
 * bağımlıdır; kaynak değiştiğinde implementasyonu swap etmek yeterlidir.</p>
 */
public interface CurrencyConversionService {

    /**
     * Belirtilen tarih ve tip için kur değerini döndürür.
     *
     * @param fromCurrency kaynak para birimi ISO 4217 kodu (örn. "EUR")
     * @param toCurrency   hedef para birimi ISO 4217 kodu (örn. "TRY")
     * @param rateDate     kurun ait olduğu tarih
     * @param rateType     kur tipi (BUYING / SELLING / CENTRAL_BANK)
     * @return kur değeri (BigDecimal, precision 18 scale 6)
     * @throws com.wms.billing.exception.ExchangeRateNotFoundException belirtilen parametreler
     *         için kur bulunamadığında fırlatılır
     */
    BigDecimal getRate(String fromCurrency, String toCurrency, LocalDate rateDate, RateType rateType);
}
