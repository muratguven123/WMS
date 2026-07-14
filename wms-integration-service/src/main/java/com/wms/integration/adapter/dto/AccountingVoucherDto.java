package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP'ye gönderilecek muhasebe fişi verisi.
 *
 * <p>İş isteri 7.4 — "muhasebe fişi aktarımı" senaryosu. WMS'te oluşan
 * finansal olaylar (fatura, iade, sayım farkı vb.) muhasebe fişi olarak
 * ERP genel muhasebesine aktarılır.
 */
@Value
@Builder
@Jacksonized
public class AccountingVoucherDto {

    /** Şirket ID — zorunlu. */
    @NotNull
    Long companyId;

    /** Kaynak lokasyon/depo ID — adaptör çözümlemesi için zorunlu. */
    @NotNull
    Long locationId;

    /** Fiş tipi (MAHSUP, TAHSIL, TEDIYE, ACILIS vb. — ERP'ye özgü kod). */
    @NotBlank
    String voucherType;

    /** Fiş tarihi. */
    @NotNull
    LocalDate voucherDate;

    /** Fiş satırları (borç/alacak dengeli olmalı). */
    List<VoucherLineDto> lines;
}
