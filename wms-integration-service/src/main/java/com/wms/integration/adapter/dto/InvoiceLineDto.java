package com.wms.integration.adapter.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Fatura satır detayı.
 */
@Value
@Builder
public class InvoiceLineDto {

    /** Satır sırası */
    Integer lineNumber;

    /** SKU */
    String sku;

    /** Ürün adı */
    String description;

    /** Miktar */
    BigDecimal quantity;

    /** Birim */
    String unit;

    /** Birim fiyat (dövizli) */
    BigDecimal unitPriceForeign;

    /** Satır toplam (dövizli) */
    BigDecimal lineTotalForeign;

    /** Satır toplam (yerel) */
    BigDecimal lineTotalLocal;
}
