package com.wms.core.controller;

import com.wms.core.dto.workflow.ProcessStepDto;
import com.wms.core.dto.workflow.WorkflowEnforceRequest;
import com.wms.core.dto.workflow.WorkflowEnforceResponse;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.WorkflowEnforcementService;
import com.wms.core.service.WorkflowValidatorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * İş akışı doğrulama ve uzak enforce REST API.
 */
@RestController
@RequestMapping("/api/workflow")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN', 'WAREHOUSE_OPERATOR', 'PICKER', 'PACKER')")
public class WorkflowController {

    private final WorkflowValidatorService workflowValidatorService;
    private final WorkflowEnforcementService workflowEnforcementService;

    public WorkflowController(
            WorkflowValidatorService workflowValidatorService,
            WorkflowEnforcementService workflowEnforcementService) {
        this.workflowValidatorService = workflowValidatorService;
        this.workflowEnforcementService = workflowEnforcementService;
    }

    /**
     * Mevcut adım tamamlandığında bir sonraki adımı belirler.
     */
    @GetMapping("/next-step")
    public ResponseEntity<ProcessStepDto> determineNextStep(
            @RequestParam @NotBlank String processCode,
            @RequestParam @NotBlank String currentStepCode,
            @RequestParam(defaultValue = "false") boolean skipOptional) {

        Long locationId = TenantContextHolder.getLocationId();

        ProcessStepDto next = workflowValidatorService.determineNextStep(
                locationId, processCode, currentStepCode, skipOptional);

        return ResponseEntity.ok(next);
    }

    /**
     * Outbound vb. uzak servislerin süreç adımını senkron zorlaması.
     * Tenant header'larından location/user okunur.
     */
    @PostMapping("/enforce")
    public ResponseEntity<WorkflowEnforceResponse> enforce(
            @Valid @RequestBody WorkflowEnforceRequest request) {
        return ResponseEntity.ok(workflowEnforcementService.enforce(request));
    }
}
