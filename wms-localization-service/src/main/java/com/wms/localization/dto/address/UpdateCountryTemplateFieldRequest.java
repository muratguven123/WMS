package com.wms.localization.dto.address;

public record UpdateCountryTemplateFieldRequest(
        Boolean mandatory,
        String validationRegex,
        String errorMessageKey
) {}
