package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.service.InboundWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mal kabul süreç adımları — WorkflowAspect entegrasyon örneği.
 */
@RestController
@RequestMapping("/api/inbound")
@PreAuthorize("hasAnyRole('INBOUND_CLERK', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class InboundWorkflowController {

    private final InboundWorkflowService inboundWorkflowService;

    public InboundWorkflowController(InboundWorkflowService inboundWorkflowService) {
        this.inboundWorkflowService = inboundWorkflowService;
    }

    @PostMapping("/receipts/{receiptId}/qc")
    public ResponseEntity<Map<String, String>> completeQc(
            @PathVariable Long receiptId,
            @RequestParam(defaultValue = "PASS") String result) {

        inboundWorkflowService.completeQcStep(receiptId, result);
        return ResponseEntity.ok(Map.of("status", "COMPLETED", "step", "QC"));
    }

    @PostMapping("/receipts/{receiptId}/putaway")
    public ResponseEntity<Map<String, String>> completePutaway(@PathVariable Long receiptId) {
        inboundWorkflowService.completePutawayStep(receiptId);
        return ResponseEntity.ok(Map.of("status", "COMPLETED", "step", "PUTAWAY"));
    }
}
