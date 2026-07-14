package com.wms.integration.adapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * ERP'ye gönderilecek iade bildirimi verisi.
 *
 * <p>İş isteri 7.4 — "iade aktarımı" senaryosu. İade her zaman bir kaynak
 * siparişe referans verir; kalemler iade edilen miktarları taşır.
 */
@Value
@Builder
@Jacksonized
public class ReturnNoticeDto {

    /** Şirket ID — zorunlu. */
    @NotNull
    Long companyId;

    /** Kaynak lokasyon/depo ID — adaptör çözümlemesi için zorunlu. */
    @NotNull
    Long locationId;

    /** İadenin bağlı olduğu kaynak sipariş numarası. */
    @NotBlank
    String referenceOrderNumber;

    /** İade nedeni (hasarlı, yanlış ürün, müşteri vazgeçti vb.). */
    String returnReason;

    /** İade kalemleri. */
    List<OrderLineDto> lines;
}
