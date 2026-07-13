package com.wms.core.dto.geo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Şehir ekleme isteği (İş İsteri 18).
 *
 * <p>{@code stateProvinceId} null ise şehir doğrudan ülkeye bağlanır
 * (eyaletsiz model); doluysa eyaletin aynı ülkeye ait olduğu doğrulanır.</p>
 */
public record CreateCityRequest(

        @NotBlank(message = "name zorunludur")
        @Size(max = 150, message = "name en fazla 150 karakter olabilir")
        String name,

        Long stateProvinceId
) {}
