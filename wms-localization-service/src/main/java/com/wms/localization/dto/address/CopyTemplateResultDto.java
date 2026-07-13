package com.wms.localization.dto.address;

public record CopyTemplateResultDto(
        Long countryId,
        Long sourceCountryId,
        int copiedCount
) {}
