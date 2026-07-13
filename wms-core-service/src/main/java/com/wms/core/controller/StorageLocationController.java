package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.dto.StorageLocationResponse;
import com.wms.core.dto.StorageLocationStatusUpdateRequest;
import com.wms.core.entity.enums.StorageLocationStatus;
import com.wms.core.service.StorageLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class StorageLocationController {

    private final StorageLocationService storageLocationService;
    private final com.wms.core.service.LocationCapacityService locationCapacityService;

    @GetMapping("/{locationId}/capacity/check")
    public ResponseEntity<Boolean> checkCapacity(
            @PathVariable Long locationId,
            @RequestParam BigDecimal volume,
            @RequestParam BigDecimal weight) {
        return ResponseEntity.ok(locationCapacityService.hasAvailableCapacity(locationId, volume, weight));
    }

    @PostMapping("/{locationId}/capacity/load")
    public ResponseEntity<Void> updateLoad(
            @PathVariable Long locationId,
            @RequestParam BigDecimal volumeDelta,
            @RequestParam BigDecimal weightDelta,
            @RequestParam boolean isAddition) {
        locationCapacityService.updateLocationLoad(locationId, volumeDelta, weightDelta, isAddition);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{locationId}/status")
    public ResponseEntity<StorageLocationResponse> updateStatus(
            @PathVariable Long locationId,
            @Valid @RequestBody StorageLocationStatusUpdateRequest request) {

        return ResponseEntity.ok(storageLocationService.updateStatus(locationId, request));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<StorageLocationResponse>> search(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) StorageLocationStatus status,
            @RequestParam(required = false) String aisle,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                storageLocationService.search(zoneId, status, aisle, isActive, pageable));
    }

    @GetMapping("/{locationId}")
    public ResponseEntity<StorageLocationResponse> getById(@PathVariable Long locationId) {
        return ResponseEntity.ok(storageLocationService.findById(locationId));
    }

    @GetMapping("/utilization")
    public ResponseEntity<List<StorageLocationResponse>> utilization(
            @RequestParam(required = false, defaultValue = "80") BigDecimal thresholdPercent) {

        return ResponseEntity.ok(storageLocationService.findCriticalUtilization(thresholdPercent));
    }
}
