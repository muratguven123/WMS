package com.wms.localization.dto.address;

import jakarta.validation.constraints.NotNull;

public record AddCountryTemplateFieldRequest(
        @NotNull Long fieldId,
        Boolean mandatory,
        Integer sequence,
        String validationRegex,
        String errorMessageKey
) {}
