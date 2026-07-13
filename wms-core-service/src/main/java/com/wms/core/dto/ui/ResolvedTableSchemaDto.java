package com.wms.core.dto.ui;

import java.time.OffsetDateTime;
import java.util.List;

public record ResolvedTableSchemaDto(
        String screenCode,
        List<ResolvedColumnDto> columns,
        OffsetDateTime resolvedAt
) {}
