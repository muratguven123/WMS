package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Sipariş / iade kalemi.
 *
 * <p>{@link PurchaseOrderDto}, {@link SalesOrderDto} ve {@link ReturnNoticeDto}
 * tarafından ortak kullanılır (aynı kalem yapısı — DRY).
 */
@Value
@Builder
@Jacksonized
public class OrderLineDto {

    /** Ürün/SKU kodu. */
    @NotBlank
    String productCode;

    /** Sipariş miktarı. */
    @NotNull
    BigDecimal quantity;

    /** Birim fiyat (satır para biriminde). */
    BigDecimal unitPrice;

    /** Satır para birimi (ISO 4217). */
    String currencyCode;

    /** KDV/vergi oranı (yüzde — örn. 20 = %20). */
    BigDecimal taxRate;
}
