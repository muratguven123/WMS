package com.wms.localization.dto.address;

/**
 * Şablona alan ekleme/güncelleme yanıtı — opsiyonel uyarı kodu içerir.
 */
public record TemplateFieldMutationResponse(
        CountryAddressTemplateDto template,
        String warning
) {}
