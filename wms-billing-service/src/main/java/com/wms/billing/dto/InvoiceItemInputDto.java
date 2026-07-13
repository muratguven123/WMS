package com.wms.billing.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Fatura satırı giriş DTO'su.
 * Servis katmanına ham veri olarak gelir; hesaplama servisi sonuçları buraya yazar.
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
         * Vergi oranı yüzde cinsinden. Örn: 20.00 = %20.
         * [0.00, 100.00] aralığında olmalıdır.
         */
        @NotNull
        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00")
        BigDecimal taxRate
) {}
