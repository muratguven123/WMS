package com.wms.core.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProcessStepDefinitionRequest(
        @NotNull Long processDefinitionId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        int defaultSequence
) {}
