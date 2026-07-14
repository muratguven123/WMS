package com.wms.billing.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Fatura satırı giriş DTO'su.
 * {@code taxTypeCode} ile finance vergi motorundan oran çözülür;
 * {@code taxRate} doluysa manuel override kullanılır.
 */
@Builder
public record InvoiceItemInputDto(

        @NotBlank(message = "Kalem açıklaması boş olamaz")
        String itemDescription,

        @NotNull @DecimalMin(value = "0.0001", message = "Miktar sıfırdan büyük olmalıdır")
        BigDecimal quantity,

        @NotNull @DecimalMin(value = "0.00", message = "Birim fiyat negatif olamaz")
        BigDecimal unitPriceOriginal,

        @NotNull @DecimalMin(value = "0.00", message = "İskonto negatif olamaz")
        BigDecimal discountOriginal,

        /**
         * Vergi tipi kodu (örn. KDV, VAT). Override yoksa finance motoru bu kodla oran çözer.
         */
        @NotBlank(message = "Vergi tipi kodu zorunludur")
        String taxTypeCode,

        /**
         * Opsiyonel manuel vergi oranı (%). Doluysa finance çağrısı atlanır.
         */
        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00")
        BigDecimal taxRate,

        String productType,

        String operationType
) {}
