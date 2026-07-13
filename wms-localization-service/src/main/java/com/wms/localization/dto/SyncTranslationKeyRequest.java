package com.wms.localization.dto;

import jakarta.validation.constraints.NotBlank;

public record SyncTranslationKeyRequest(
        @NotBlank String keyCode,
        String module,
        String description,
        String tr,
        String en
) {}
