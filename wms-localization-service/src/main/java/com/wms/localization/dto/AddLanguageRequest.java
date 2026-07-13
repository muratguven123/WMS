package com.wms.localization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeni dil ekleme isteği.
 */
public record AddLanguageRequest(
        @NotBlank(message = "code is required")
        @Size(min = 2, max = 10)
        @Pattern(regexp = "^[a-zA-Z]{2,10}$", message = "code must be a 2-letter ISO 639-1 code (e.g. ru, de)")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 100)
        String name
) {
}
