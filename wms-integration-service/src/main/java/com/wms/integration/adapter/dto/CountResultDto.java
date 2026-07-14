package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP'ye gönderilecek sayım sonucu verisi.
 *
 * <p>İş isteri 7.4 — "sayım sonucu aktarımı" senaryosu. Depo sayımı
 * tamamlandığında beklenen/sayılan miktar farkları ERP'ye bildirilir;
 * ERP tarafında stok düzeltme fişine dönüştürülür.
 */
@Value
@Builder
@Jacksonized
public class CountResultDto {

    /** Şirket ID — zorunlu. */
    @NotNull
    Long companyId;

    /** Kaynak lokasyon/depo ID — adaptör çözümlemesi için zorunlu. */
    @NotNull
    Long locationId;

    /** WMS sayım ID. */
    @NotNull
    Long countId;

    /** Sayım tarihi. */
    @NotNull
    LocalDate countDate;

    /** Sayım kalemleri. */
    List<CountLineDto> lines;
}
