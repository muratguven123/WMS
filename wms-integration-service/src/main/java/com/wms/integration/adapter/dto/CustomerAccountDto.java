package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * ERP'ye gönderilecek cari hesap (müşteri/tedarikçi) kartı verisi.
 *
 * <p>İş isteri 7.4 — "cari hesap aktarımı" senaryosu. Outbox payload'ı olarak
 * serileştirilir; {@code @Jacksonized} sayesinde worker tarafında builder
 * üzerinden güvenle deserialize edilir.
 */
@Value
@Builder
@Jacksonized
public class CustomerAccountDto {

    /** Şirket ID — tenant ayrımı ve ERP şirket kodu çözümlemesi için zorunlu. */
    @NotNull
    Long companyId;

    /** Kaynak lokasyon/depo ID — adaptör çözümlemesi için zorunlu. */
    @NotNull
    Long locationId;

    /** Cari hesap kodu (ERP tarafındaki benzersiz müşteri/tedarikçi kodu). */
    @NotBlank
    String customerCode;

    /** Cari hesap ünvanı. */
    @NotBlank
    String name;

    /** Vergi numarası (TCKN/VKN veya ülkeye özgü vergi kimliği). */
    String taxNumber;

    /** Cari hesabın çalışma para birimi (ISO 4217: TRY, USD, EUR vb.). */
    String currencyCode;

    /** Açık adres. */
    String address;
}
