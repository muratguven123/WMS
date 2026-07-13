package com.wms.core.dto.ui;

import com.wms.core.entity.enums.ColumnDataType;

/**
 * Çözümlenmiş tablo kolonu — frontend DataTable için.
 */
public record ResolvedColumnDto(
        Long columnDefId,
        String key,
        String labelKey,
        ColumnDataType dataType,
        boolean visible,
        int sequence,
        boolean locked,
        String renderHint,
        boolean forceHidden,
        boolean forceVisible
) {}
