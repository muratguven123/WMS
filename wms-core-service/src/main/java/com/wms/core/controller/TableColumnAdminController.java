package com.wms.core.controller;

import com.wms.core.dto.ui.*;
import com.wms.core.service.TableColumnAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tablo kolon tanımı ve rol kuralı admin API'si (İş İsteri 16).
 */
@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('UI_CONFIG_ADMIN', 'WMS_ADMIN')")
public class TableColumnAdminController {

    private final TableColumnAdminService adminService;

    @GetMapping("/screens/{screenCode}/columns")
    public ResponseEntity<List<ColumnDefResponse>> listColumns(@PathVariable String screenCode) {
        return ResponseEntity.ok(adminService.listColumnDefs(screenCode));
    }

    @PostMapping("/screens/{screenCode}/columns")
    public ResponseEntity<ColumnDefResponse> createColumn(
            @PathVariable String screenCode,
            @Valid @RequestBody UpsertColumnDefRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createColumnDef(screenCode, request));
    }

    @PutMapping("/screens/{screenCode}/columns/{columnDefId}")
    public ResponseEntity<ColumnDefResponse> updateColumn(
            @PathVariable String screenCode,
            @PathVariable Long columnDefId,
            @Valid @RequestBody UpsertColumnDefRequest request) {
        return ResponseEntity.ok(adminService.updateColumnDef(screenCode, columnDefId, request));
    }

    @DeleteMapping("/screens/{screenCode}/columns/{columnDefId}")
    public ResponseEntity<Void> deleteColumn(
            @PathVariable String screenCode,
            @PathVariable Long columnDefId) {
        adminService.deleteColumnDef(screenCode, columnDefId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/column-rules")
    public ResponseEntity<List<ColumnRuleResponse>> listRules(
            @RequestParam String screenCode) {
        return ResponseEntity.ok(adminService.listColumnRules(screenCode));
    }

    @PostMapping("/column-rules")
    public ResponseEntity<ColumnRuleResponse> createRule(
            @Valid @RequestBody UpsertColumnRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createColumnRule(request));
    }

    @DeleteMapping("/column-rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long ruleId) {
        adminService.deleteColumnRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
