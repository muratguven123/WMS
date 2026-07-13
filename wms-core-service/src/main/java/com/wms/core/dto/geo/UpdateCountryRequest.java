package com.wms.core.dto.geo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ülke güncelleme isteği (İş İsteri 18).
 *
 * <p>{@code isoCode} güncellenemez — localization-service JSONB kayıtlarında
 * {@code countryIso} olarak saklandığı için değişmezdir (Teknik İş Kuralı 1).</p>
 */
public record UpdateCountryRequest(

        @NotBlank(message = "name zorunludur")
        @Size(max = 100, message = "name en fazla 100 karakter olabilir")
        String name
) {}
