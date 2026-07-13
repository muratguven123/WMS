package com.wms.core.controller;

import com.wms.core.dto.audit.AuditLogEntryDto;
import com.wms.core.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLogEntryDto>> listLogs(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) Long changedByUserId,
            @PageableDefault(size = 20, sort = "changedAt") Pageable pageable) {
        return ResponseEntity.ok(auditLogQueryService.search(entityName, changedByUserId, pageable));
    }
}
