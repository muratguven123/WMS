package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ERP'den çekilen vergi bilgisi (INBOUND senaryo).
 *
 * <p>İş isteri 7.4 — "vergi bilgisi aktarımı" senaryosu.
 * {@link com.wms.integration.adapter.ErpAdapter#fetchTaxInfo()} ile ERP'den
 * çekilir; wms-localization-service'teki vergi master data'sının
 * senkronizasyonunda kullanılır.
 */
@Value
@Builder
@Jacksonized
public class TaxInfoDto {

    /** Şirket ID — zorunlu. */
    @NotNull
    Long companyId;

    /** Kaynak lokasyon/depo ID — hangi lokasyonun ERP'sinden çekildiği. */
    @NotNull
    Long locationId;

    /** Vergi tipi kodu (KDV_STANDART, KDV_INDIRIMLI, OTV, STOPAJ vb.). */
    @NotBlank
    String taxTypeCode;

    /** Vergi oranı (yüzde — örn. 20 = %20). */
    @NotNull
    BigDecimal rate;

    /** Ülke kodu (ISO 3166-1 alpha-2: TR, DE, US vb.). */
    String countryCode;

    /** Geçerlilik başlangıcı. */
    LocalDate validFrom;

    /** Geçerlilik sonu (null = süresiz). */
    LocalDate validTo;
}
