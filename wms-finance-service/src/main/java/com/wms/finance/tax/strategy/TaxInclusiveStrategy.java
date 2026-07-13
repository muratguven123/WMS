package com.wms.finance.tax.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Vergili (KDV Dahil) hesaplama stratejisi.
 *
 * <p><b>Formüller:</b>
 * <pre>
 *   BaseAmount = GrandTotal / (1 + Rate / 100)
 *   TaxAmount  = GrandTotal - BaseAmount
 * </pre>
 *
 * <p>Kullanım: Faturada toplam tutar KDV dahil girilir, matrah geri hesaplanır.
 * Bu durum perakende fiyatlandırmasında yaygındır (etiket fiyatı = brüt fiyat).
 */
@Component("taxInclusiveStrategy")
public class TaxInclusiveStrategy implements TaxCalculationStrategy {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_EVEN);
    private static final int MONETARY_SCALE = 2;

    /**
     * @param baseAmount bu stratejide <b>grandTotal</b> (KDV dahil tutar) olarak yorumlanır
     * @param rate       vergi oranı (yüzde, örn: 18.0 → %18)
     */
    @Override
    public TaxCalculationResult calculateTax(BigDecimal baseAmount, BigDecimal rate) {
        validate(baseAmount, rate);

        BigDecimal grandTotal = baseAmount; // semantik netlik için alias

        // BaseAmount = GrandTotal / (1 + Rate / 100)
        BigDecimal divisor   = BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), MC));
        BigDecimal netAmount = grandTotal.divide(divisor, MC)
                                         .setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);

        // TaxAmount = GrandTotal - BaseAmount
        BigDecimal taxAmount      = grandTotal.subtract(netAmount)
                                              .setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal scaledGrand    = grandTotal.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);

        // Yuvarlama farkı nedeniyle netAmount + taxAmount != scaledGrand olabilir;
        // grandTotal'ı netAmount + taxAmount olarak yeniden türet (tutarlılık kaydı).
        BigDecimal consistentGrand = netAmount.add(taxAmount);

        return new TaxCalculationResult(netAmount, taxAmount, consistentGrand);
    }

    private void validate(BigDecimal baseAmount, BigDecimal rate) {
        if (baseAmount == null || rate == null) {
            throw new IllegalArgumentException("Tutar ve oran null olamaz.");
        }
        if (baseAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Brüt tutar negatif olamaz: " + baseAmount);
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Vergi oranı 0-100 arasında olmalıdır: " + rate);
        }
    }
}
