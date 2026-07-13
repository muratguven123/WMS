package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.enums.ApprovalStatus;
import com.wms.core.repository.ApprovalRequestRepository;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.ApprovalRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class ApprovalController {

    private final ApprovalRequestService approvalRequestService;
    private final ApprovalRequestRepository approvalRequestRepository;

    public ApprovalController(ApprovalRequestService approvalRequestService,
                              ApprovalRequestRepository approvalRequestRepository) {
        this.approvalRequestService = approvalRequestService;
        this.approvalRequestRepository = approvalRequestRepository;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalRequest>> listPending() {
        return ResponseEntity.ok(
                approvalRequestRepository.findByStatusAndCompanyIdAndLocationIdOrderByCreatedAtAsc(
                        ApprovalStatus.PENDING_APPROVAL,
                        TenantContextHolder.getCompanyId(),
                        TenantContextHolder.getLocationId()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalRequest> approve(
            @PathVariable Long id,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(approvalRequestService.approve(id, note));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApprovalRequest> reject(
            @PathVariable Long id,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(approvalRequestService.reject(id, note));
    }
}
