package com.wms.core.dto.geo;

import jakarta.validation.constraints.NotNull;

/**
 * Mevcut bir şehri eyalete bağlama isteği (İş İsteri 18 — TR eyalet geçiş senaryosu).
 *
 * <p>Bir ülkeye sonradan eyalet sistemi eklendiğinde, doğrudan ülkeye bağlı
 * şehirler bu endpoint ile kademeli olarak eyaletlere taşınır (Teknik İş Kuralı 4).</p>
 */
public record AssignStateRequest(

        @NotNull(message = "stateProvinceId zorunludur")
        Long stateProvinceId
) {}
