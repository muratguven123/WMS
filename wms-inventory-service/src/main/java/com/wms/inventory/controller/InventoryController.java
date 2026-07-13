package com.wms.inventory.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.inventory.dto.AllocateStockRequest;
import com.wms.inventory.dto.AllocatedStockDto;
import com.wms.inventory.dto.InternalMoveRequest;
import com.wms.inventory.dto.InventoryAdjustRequest;
import com.wms.inventory.dto.InventoryIssueRequest;
import com.wms.inventory.service.InventoryAllocationService;
import com.wms.inventory.service.InventoryIssueService;
import com.wms.inventory.service.InventoryMoveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INVENTORY_CLERK', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class InventoryController {

    private final InventoryMoveService inventoryMoveService;
    private final InventoryAllocationService inventoryAllocationService;
    private final InventoryIssueService inventoryIssueService;

    @PostMapping("/allocate")
    public ResponseEntity<List<AllocatedStockDto>> allocateStock(
            @Valid @RequestBody AllocateStockRequest request) {

        List<AllocatedStockDto> allocations = inventoryAllocationService.allocateStock(
                request.productCode(),
                request.requiredQuantity(),
                request.allocationStrategy(),
                request.warehouseLocationId());
        return ResponseEntity.ok(allocations);
    }

    /**
     * POST /api/inventory/move
     * Executes internal stock movement between two storage location Bins.
     */
    @PostMapping("/move")
    public ResponseEntity<Void> moveStock(@Valid @RequestBody InternalMoveRequest request) {
        log.info("REST request to move stock: {}", request);
        inventoryMoveService.moveStock(request);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/inventory/count/adjust
     * Executes cycle counting adjustment (adds or reduces stock and updates loads).
     */
    /** Stok düzeltme hassas bir işlemdir — class-level kuralı override eder. */
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
    @PostMapping("/count/adjust")
    public ResponseEntity<Void> adjustStock(@Valid @RequestBody InventoryAdjustRequest request) {
        log.info("REST request to adjust stock: {}", request);
        inventoryMoveService.adjustStock(request);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/inventory/issue
     * Deducts ALLOCATED stock when a shipment is dispatched (warehouse exit).
     */
    @PostMapping("/issue")
    public ResponseEntity<Void> issueStock(@Valid @RequestBody InventoryIssueRequest request) {
        log.info("REST request to issue stock for shipment: {}", request.shipmentNumber());
        inventoryIssueService.issueStock(request);
        return ResponseEntity.ok().build();
    }
}
