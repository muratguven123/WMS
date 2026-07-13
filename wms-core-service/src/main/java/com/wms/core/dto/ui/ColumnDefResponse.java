package com.wms.core.dto.ui;

import com.wms.core.entity.enums.ColumnBehavior;
import com.wms.core.entity.enums.ColumnDataType;

public record ColumnDefResponse(
        Long id,
        String screenCode,
        String columnKey,
        String labelKey,
        ColumnDataType dataType,
        boolean defaultVisible,
        int defaultSequence,
        boolean locked,
        String renderHint
) {}
