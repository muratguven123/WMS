package com.wms.finance.tax.strategy;

import java.math.BigDecimal;

/**
 * Vergi hesaplama sonucunu taşıyan immutable DTO.
 *
 * @param baseAmount  Matrah (vergisiz tutar)
 * @param taxAmount   Hesaplanan vergi tutarı
 * @param grandTotal  Genel toplam (matrah + vergi)
 */
public record TaxCalculationResult(
        BigDecimal baseAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal
) {

    /**
     * Compact constructor: null kontrolü ve tutarlılık doğrulaması.
     */
    public TaxCalculationResult {
        if (baseAmount == null || taxAmount == null || grandTotal == null) {
            throw new IllegalArgumentException("Vergi hesaplama sonucu alanları null olamaz.");
        }
        // baseAmount + taxAmount == grandTotal olmalı (2 decimal hassasiyette)
        BigDecimal expected = baseAmount.add(taxAmount);
        if (expected.compareTo(grandTotal) != 0) {
            throw new IllegalArgumentException(
                    "Tutarsız vergi sonucu: baseAmount(%s) + taxAmount(%s) = %s, ancak grandTotal=%s"
                            .formatted(baseAmount, taxAmount, expected, grandTotal)
            );
        }
    }

    /**
     * Sonucu okunabilir biçimde döner.
     */
    @Override
    public String toString() {
        return "TaxCalculationResult{baseAmount=%s, taxAmount=%s, grandTotal=%s}"
                .formatted(baseAmount, taxAmount, grandTotal);
    }
}
