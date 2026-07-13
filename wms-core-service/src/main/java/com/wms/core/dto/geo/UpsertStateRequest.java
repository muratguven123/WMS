package com.wms.core.dto.geo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Eyalet / il ekleme-güncelleme isteği (İş İsteri 18).
 *
 * @param code opsiyonel kısa kod (örn. "CA", "34") — ülke içinde tekildir
 */
public record UpsertStateRequest(

        @NotBlank(message = "name zorunludur")
        @Size(max = 150, message = "name en fazla 150 karakter olabilir")
        String name,

        @Size(max = 10, message = "code en fazla 10 karakter olabilir")
        String code
) {}
