package com.wms.localization.dto.address;

import jakarta.validation.constraints.NotNull;

public record ReorderEntryDto(
        @NotNull Long templateId,
        @NotNull Integer sequence
) {}
