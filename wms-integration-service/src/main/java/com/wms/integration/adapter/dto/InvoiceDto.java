package com.wms.integration.adapter.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ERP'ye gönderilecek fatura verisi.
 */
@Value
@Builder
public class InvoiceDto {

    /** WMS fatura ID */
    Long invoiceId;

    /** Fatura numarası */
    String invoiceNumber;

    /** Fatura tarihi */
    LocalDate invoiceDate;

    /** Müşteri/cari kodu */
    String partnerCode;

    /** Döviz kodu (USD, EUR, TRY vb.) */
    String currencyCode;

    /** Fatura dövizli toplam tutar */
    BigDecimal totalAmountForeign;

    /** Fatura yerel para karşılığı (kur kilitlenmiş) */
    BigDecimal totalAmountLocal;

    /** Kilitlenmiş kur */
    BigDecimal lockedExchangeRate;

    /** Kaynak lokasyon ID */
    Long locationId;

    /** Fatura satırları */
    List<InvoiceLineDto> lines;
}
