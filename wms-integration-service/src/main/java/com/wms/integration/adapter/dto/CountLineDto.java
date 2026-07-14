package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Sayım sonucu kalemi — beklenen/sayılan miktar ve fark.
 */
@Value
@Builder
@Jacksonized
public class CountLineDto {

    /** Ürün/SKU kodu. */
    @NotBlank
    String productCode;

    /** Sistemdeki beklenen miktar. */
    @NotNull
    BigDecimal expectedQty;

    /** Fiziksel sayımda bulunan miktar. */
    @NotNull
    BigDecimal countedQty;

    /** Fark = countedQty - expectedQty (negatif: eksik, pozitif: fazla). */
    BigDecimal difference;
}
