package com.wms.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record CreateInvoiceRequest(
        @NotNull(message = "Müşteri kimliği zorunludur")
        Long customerId,

        @NotBlank(message = "Fatura para birimi zorunludur")
        @Size(min = 3, max = 3, message = "Para birimi ISO 4217 formatında 3 karakter olmalıdır")
        String invoiceCurrency,

        @NotNull(message = "Kur tarihi zorunludur")
        LocalDate exchangeRateDate,

        /** Ülke kimliği — vergi çözümlemesi için; yoksa billing.default-country-id */
        Long countryId,

        @NotEmpty(message = "En az bir fatura satırı gereklidir")
        @Valid
        List<InvoiceItemInputDto> items
) {}
