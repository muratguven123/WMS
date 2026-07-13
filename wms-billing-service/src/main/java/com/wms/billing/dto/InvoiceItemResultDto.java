package com.wms.billing.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Hesaplama motorunun her bir satır için ürettiği sonuç DTO'su.
 */
@Builder
public record InvoiceItemResultDto(
        String itemDescription,
        BigDecimal quantity,
        BigDecimal unitPriceOriginal,
        BigDecimal discountOriginal,
        BigDecimal taxRate,

        /** (quantity × unitPriceOriginal) − discountOriginal */
        BigDecimal lineTotalOriginal,

        /** lineTotalOriginal × (taxRate / 100) */
        BigDecimal taxAmountOriginal
) {}
