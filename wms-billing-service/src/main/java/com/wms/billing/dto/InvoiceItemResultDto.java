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
        String taxTypeCode,
        BigDecimal taxRate,

        /** (quantity × unitPriceOriginal) − discountOriginal */
        BigDecimal lineTotalOriginal,

        /** lineTotalOriginal × (taxRate / 100) veya finance motor sonucu */
        BigDecimal taxAmountOriginal
) {}
