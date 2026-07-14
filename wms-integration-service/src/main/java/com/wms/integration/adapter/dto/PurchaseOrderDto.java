package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP'ye gönderilecek satın alma siparişi verisi.
 *
 * <p>İş isteri 7.4 — "satın alma siparişi aktarımı" senaryosu.
 */
@Value
@Builder
@Jacksonized
public class PurchaseOrderDto {

    /** Şirket ID — zorunlu. */
    @NotNull
    Long companyId;

    /** Kaynak lokasyon/depo ID — adaptör çözümlemesi için zorunlu. */
    @NotNull
    Long locationId;

    /** WMS sipariş numarası. */
    @NotBlank
    String orderNumber;

    /** Sipariş tarihi. */
    @NotNull
    LocalDate orderDate;

    /** Cari (tedarikçi) kodu — {@link CustomerAccountDto#getCustomerCode()} ile örtüşür. */
    @NotBlank
    String customerCode;

    /** Sipariş kalemleri. */
    List<OrderLineDto> lines;
}
