package com.wms.core.entity.audit;

public record ProcessStepDefinitionSnapshot(
        String code,
        String name,
        int defaultSequence,
        boolean active
) {}
