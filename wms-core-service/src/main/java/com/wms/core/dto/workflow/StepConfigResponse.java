package com.wms.core.dto.workflow;


public record StepConfigResponse(
        Long id,
        String stepCode,
        String stepName,
        int sequence,
        boolean mandatory,
        boolean active,
        boolean requiresApproval,
        Long responsibleRoleId,
        String processCode
) {}
