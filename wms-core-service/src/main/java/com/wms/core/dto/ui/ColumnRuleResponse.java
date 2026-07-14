package com.wms.core.dto.ui;

import com.wms.core.entity.enums.ColumnBehavior;

public record ColumnRuleResponse(
        Long id,
        Long tableColumnDefId,
        String columnKey,
        int priority,
        Long roleId,
        Long companyId,
        Long warehouseId,
        String customerType,
        String productType,
        String transactionStatus,
        ColumnBehavior behavior
) {}
