package com.wms.billing.dto;

import com.wms.billing.domain.enums.InvoiceStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Hesaplama motorunun döndürdüğü tam fatura DTO'su.
 * Persist edilmeden önce entity'e map edilir.
 */
@Builder
public record InvoiceDto(
        Long customerId,
        Long locationId,

        /** ISO 4217 fatura para birimi kodu (örn. EUR, USD). */
        String invoiceCurrency,

        /** ISO 4217 muhasebe para birimi kodu (örn. TRY, EUR). */
        String accountingCurrency,

        LocalDate exchangeRateDate,

        /** Faturalama anında dondurulmuş kur (SELLING tipi). */
        BigDecimal exchangeRateValue,

        List<InvoiceItemResultDto> items,

        /** Satırların lineTotalOriginal toplamı (vergi hariç). */
        BigDecimal subtotalOriginal,

        /** Satırların taxAmountOriginal toplamı. */
        BigDecimal taxAmountOriginal,

        /** subtotalOriginal + taxAmountOriginal */
        BigDecimal grandTotalOriginal,

        /** grandTotalOriginal × exchangeRateValue */
        BigDecimal grandTotalAccounting,

        InvoiceStatus status
) {}
