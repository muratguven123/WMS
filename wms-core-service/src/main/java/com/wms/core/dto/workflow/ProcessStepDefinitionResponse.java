package com.wms.core.dto.workflow;

public record ProcessStepDefinitionResponse(
        Long id,
        Long processDefinitionId,
        String processDefinitionCode,
        String code,
        String name,
        int defaultSequence,
        boolean isActive
) {}
