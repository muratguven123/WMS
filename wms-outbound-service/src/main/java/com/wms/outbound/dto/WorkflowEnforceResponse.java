package com.wms.outbound.dto;

/**
 * Core {@code POST /api/workflow/enforce} yanıtı.
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
}
