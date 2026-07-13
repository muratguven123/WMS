package com.wms.core.dto.ui;

import com.wms.core.entity.enums.ColumnBehavior;
import jakarta.validation.constraints.NotNull;

public record UpsertColumnRuleRequest(
        @NotNull Long tableColumnDefId,
        Integer priority,
        Long roleId,
        Long companyId,
        @NotNull ColumnBehavior behavior
) {}
