package com.wms.localization.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAddressTemplateFieldRequest(
        @NotBlank @Size(max = 100) String fieldKey,
        @NotBlank @Size(max = 255) String fieldLabelKey,
        @NotBlank @Pattern(regexp = "TEXT|MASTER_SELECT|FIXED") String fieldType,
        @NotBlank @Pattern(regexp = "NONE|STATE|CITY|DISTRICT|NEIGHBORHOOD") String masterDataSource,
        @Size(max = 100) String parentFieldKey
) {}
