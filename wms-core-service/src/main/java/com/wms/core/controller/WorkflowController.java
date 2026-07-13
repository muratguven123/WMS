package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.dto.workflow.ProcessStepDto;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.WorkflowValidatorService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * İş akışı doğrulama REST API.
 */
@RestController
@RequestMapping("/api/workflow")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class WorkflowController {

    private final WorkflowValidatorService workflowValidatorService;

    public WorkflowController(WorkflowValidatorService workflowValidatorService) {
        this.workflowValidatorService = workflowValidatorService;
    }

    /**
     * Mevcut adım tamamlandığında bir sonraki adımı belirler.
     *
     * @param processCode     INBOUND, OUTBOUND vb.
     * @param currentStepCode Tamamlanan adım kodu
     * @param skipOptional    true ise opsiyonel adımlar atlanır
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
}
