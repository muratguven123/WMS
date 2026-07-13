package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.dto.workflow.StepConfigResponse;
import com.wms.core.dto.workflow.UpdateStepConfigRequest;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.service.ProcessConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/process-config/steps")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class ProcessConfigController {

    private final ProcessConfigService processConfigService;

    public ProcessConfigController(ProcessConfigService processConfigService) {
        this.processConfigService = processConfigService;
    }

    @GetMapping
    public ResponseEntity<List<StepConfigResponse>> listSteps(
            @RequestParam Long locationId,
            @RequestParam(required = false) String processCode) {
        return ResponseEntity.ok(processConfigService.listSteps(locationId, processCode));
    }

    @GetMapping("/{stepConfigId}")
    public ResponseEntity<LocationProcessStepConfig> getStepConfig(@PathVariable Long stepConfigId) {
        return ResponseEntity.ok(processConfigService.getStepConfig(stepConfigId));
    }

    @PutMapping("/{stepConfigId}")
    public ResponseEntity<LocationProcessStepConfig> updateStepConfig(
            @PathVariable Long stepConfigId,
            @Valid @RequestBody UpdateStepConfigRequest request) {
        return ResponseEntity.ok(processConfigService.updateStepConfig(stepConfigId, request));
    }
}
