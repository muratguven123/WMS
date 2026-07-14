package com.wms.core.dto.workflow;

import jakarta.validation.constraints.NotBlank;

/**
 * Uzak servislerin (outbound vb.) süreç adımı zorlaması için istek gövdesi.
 */
public record WorkflowEnforceRequest(
        @NotBlank String processCode,
        @NotBlank String stepCode,
        Long referenceId,
        String referenceType
) {
}
