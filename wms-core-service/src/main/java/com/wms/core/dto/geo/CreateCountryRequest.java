package com.wms.core.dto.geo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeni ülke oluşturma isteği (İş İsteri 18).
 *
 * <p>{@code isoCode} ISO 3166-1 alpha-2/3 formatındadır; servis katmanında
 * uppercase'e normalize edilir ve tekilliği (pasif kayıtlar dahil) kontrol edilir.</p>
 */
public record CreateCountryRequest(

        @NotBlank(message = "isoCode zorunludur")
        @Pattern(regexp = "^[A-Za-z]{2,3}$", message = "isoCode ISO 3166-1 formatında (2-3 harf) olmalıdır")
        String isoCode,

        @NotBlank(message = "name zorunludur")
        @Size(max = 100, message = "name en fazla 100 karakter olabilir")
        String name
) {}
