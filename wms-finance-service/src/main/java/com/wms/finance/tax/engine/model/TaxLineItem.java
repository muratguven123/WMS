package com.wms.finance.tax.engine.model;

import java.math.BigDecimal;

/**
 * Engine'e giren tekil kalem (fatura satırı, stok hareketi vb.).
 *
 * @param lineId      kalemin benzersiz ID'si (raporlamada referans olarak kullanılır)
 * @param description açıklama (loglarda okunabilirlik için)
 * @param amount      kalemin tutarı (stratejiye göre matrah veya brüt tutar)
 */
public record TaxLineItem(
        Long lineId,
        String description,
        BigDecimal amount
) {
    public TaxLineItem {
        if (lineId == null)  throw new IllegalArgumentException("lineId null olamaz.");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Kalem tutarı negatif olamaz.");
    }
}
