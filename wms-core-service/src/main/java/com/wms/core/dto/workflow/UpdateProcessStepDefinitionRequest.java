package com.wms.core.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProcessStepDefinitionRequest(
        @NotBlank @Size(max = 200) String name,
        int defaultSequence,
        boolean isActive
) {}
