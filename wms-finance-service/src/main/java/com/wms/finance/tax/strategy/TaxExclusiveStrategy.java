package com.wms.finance.tax.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Vergisiz (KDV Hariç) hesaplama stratejisi.
 *
 * <p><b>Formüller:</b>
 * <pre>
 *   TaxAmount  = BaseAmount × (Rate / 100)
 *   GrandTotal = BaseAmount + TaxAmount
 * </pre>
 *
 * <p>Kullanım: Faturada tutar KDV'siz girilir, vergi üstüne eklenir.
 */
@Component("taxExclusiveStrategy")
public class TaxExclusiveStrategy implements TaxCalculationStrategy {

    /** Finansal hesaplamalar için 10 basamak hassasiyet, Banker's Rounding. */
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_EVEN);

    /** Nihai para tutarı için kullanılan ondalık basamak sayısı. */
    private static final int MONETARY_SCALE = 2;

    @Override
    public TaxCalculationResult calculateTax(BigDecimal baseAmount, BigDecimal rate) {
        validate(baseAmount, rate);

        // TaxAmount = BaseAmount * (Rate / 100)
        BigDecimal taxRate   = rate.divide(BigDecimal.valueOf(100), MC);
        BigDecimal taxAmount = baseAmount.multiply(taxRate, MC)
                                         .setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);

        BigDecimal grandTotal = baseAmount.add(taxAmount)
                                          .setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);

        // baseAmount'u da aynı scale'e getir (tutarlılık için)
        BigDecimal scaledBase = baseAmount.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);

        return new TaxCalculationResult(scaledBase, taxAmount, grandTotal);
    }

    private void validate(BigDecimal baseAmount, BigDecimal rate) {
        if (baseAmount == null || rate == null) {
            throw new IllegalArgumentException("Tutar ve oran null olamaz.");
        }
        if (baseAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Matrah negatif olamaz: " + baseAmount);
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Vergi oranı 0-100 arasında olmalıdır: " + rate);
        }
    }
}
