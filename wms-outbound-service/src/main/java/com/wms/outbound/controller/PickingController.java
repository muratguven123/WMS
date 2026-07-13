package com.wms.outbound.controller;

import com.wms.outbound.dto.AssignPickingListRequest;
import com.wms.outbound.dto.ConfirmPickRequest;
import com.wms.outbound.dto.CreatePickingListRequest;
import com.wms.outbound.dto.PickingListResponse;
import com.wms.outbound.service.PickingRoutingService;
import com.wms.outbound.service.PickingTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/picking")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PICKER', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class PickingController {

    private final PickingRoutingService pickingRoutingService;
    private final PickingTaskService pickingTaskService;

    @GetMapping("/lists")
    public ResponseEntity<Page<PickingListResponse>> listPickingLists(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(pickingRoutingService.listPickingLists(pageable));
    }

    @PostMapping("/lists")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<PickingListResponse> createPickingList(
            @Valid @RequestBody CreatePickingListRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                pickingRoutingService.createPickingList(
                        request.outboundOrderIds(),
                        request.warehouseLocationId(),
                        request.createdByUserId()));
    }

    @GetMapping("/tasks/my")
    public ResponseEntity<List<PickingListResponse>> listMyTasks() {
        return ResponseEntity.ok(pickingTaskService.listMyTasks());
    }

    @GetMapping("/tasks/unassigned")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<List<PickingListResponse>> listUnassignedTasks() {
        return ResponseEntity.ok(pickingTaskService.listUnassigned());
    }

    @PostMapping("/lists/{id}/assign")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<PickingListResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignPickingListRequest request) {
        return ResponseEntity.ok(pickingTaskService.assign(id, request.assignedUserId()));
    }

    @PostMapping("/lists/{id}/start")
    public ResponseEntity<PickingListResponse> start(@PathVariable Long id) {
        return ResponseEntity.ok(pickingTaskService.start(id));
    }

    @PostMapping("/items/{itemId}/confirm")
    public ResponseEntity<PickingListResponse> confirmPick(
            @PathVariable Long itemId,
            @Valid @RequestBody ConfirmPickRequest request) {
        return ResponseEntity.ok(pickingTaskService.confirmPick(itemId, request.pickedQty()));
    }
}
