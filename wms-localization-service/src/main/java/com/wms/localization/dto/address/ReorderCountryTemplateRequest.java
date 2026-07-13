package com.wms.localization.dto.address;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderCountryTemplateRequest(
        @NotEmpty List<@Valid ReorderEntryDto> entries
) {}
