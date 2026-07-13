package com.wms.finance.tax.strategy;

import java.math.BigDecimal;

/**
 * Vergi hesaplama stratejilerinin ortak sözleşmesi.
 *
 * <p>Her strateji implementasyonu, verilen matrah ve oran üzerinden
 * kendi formülünü uygulayarak {@link TaxCalculationResult} döner.
 * Yuvarlama için tüm implementasyonlar {@code RoundingMode.HALF_EVEN}
 * (Banker's Rounding) kullanmalıdır.</p>
 */
public interface TaxCalculationStrategy {

    /**
     * Vergi hesaplar.
     *
     * @param baseAmount matrah veya brüt tutar (strateji tipine göre değişir)
     * @param rate       vergi oranı (yüzde cinsinden, örn: 20.0 → %20)
     * @return hesaplama sonucu
     * @throws IllegalArgumentException negatif tutar veya oran girilirse
     */
    TaxCalculationResult calculateTax(BigDecimal baseAmount, BigDecimal rate);
}
