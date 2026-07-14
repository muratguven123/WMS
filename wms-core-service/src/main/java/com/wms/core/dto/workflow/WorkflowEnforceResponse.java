package com.wms.core.dto.workflow;

/**
 * Süreç adımı enforce sonucu.
 */
public record WorkflowEnforceResponse(
        Decision decision,
        Long approvalRequestId,
        Long stepConfigId,
        String processCode,
        String stepCode
) {
    public enum Decision {
        ALLOWED,
        BYPASSED,
        APPROVAL_REQUIRED
    }

    public static WorkflowEnforceResponse allowed(Long stepConfigId, String processCode, String stepCode) {
        return new WorkflowEnforceResponse(Decision.ALLOWED, null, stepConfigId, processCode, stepCode);
    }

    public static WorkflowEnforceResponse bypassed(String processCode, String stepCode) {
        return new WorkflowEnforceResponse(Decision.BYPASSED, null, null, processCode, stepCode);
    }

    public static WorkflowEnforceResponse approvalRequired(
            Long approvalRequestId, Long stepConfigId, String processCode, String stepCode) {
        return new WorkflowEnforceResponse(
                Decision.APPROVAL_REQUIRED, approvalRequestId, stepConfigId, processCode, stepCode);
    }
}
