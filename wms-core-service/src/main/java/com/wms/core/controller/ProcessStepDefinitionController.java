package com.wms.core.controller;

import com.wms.core.dto.workflow.ProcessStepDefinitionRequest;
import com.wms.core.dto.workflow.UpdateProcessStepDefinitionRequest;
import com.wms.core.dto.workflow.ProcessStepDefinitionResponse;
import com.wms.core.service.ProcessStepDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/process-step-definitions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WMS_ADMIN')")
public class ProcessStepDefinitionController {

    private final ProcessStepDefinitionService processStepDefinitionService;

    @GetMapping
    public ResponseEntity<List<ProcessStepDefinitionResponse>> listAll() {
        return ResponseEntity.ok(processStepDefinitionService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessStepDefinitionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(processStepDefinitionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ProcessStepDefinitionResponse> create(@Valid @RequestBody ProcessStepDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(processStepDefinitionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProcessStepDefinitionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProcessStepDefinitionRequest request,
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(processStepDefinitionService.update(id, request, force));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        processStepDefinitionService.delete(id, force);
        return ResponseEntity.noContent().build();
    }
}
