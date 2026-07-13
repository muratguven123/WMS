package com.wms.core.dto.ui;

import com.wms.core.entity.enums.ColumnDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertColumnDefRequest(
        @NotBlank @Size(max = 100) String columnKey,
        @NotBlank @Size(max = 255) String labelKey,
        @NotNull ColumnDataType dataType,
        boolean defaultVisible,
        int defaultSequence,
        boolean locked,
        @Size(max = 50) String renderHint
) {}
