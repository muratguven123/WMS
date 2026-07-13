package com.wms.core.controller;

import com.wms.core.dto.org.CreateLocationRequest;
import com.wms.core.dto.org.LocationDetailDto;
import com.wms.core.dto.org.UpdateLocationRequest;
import com.wms.core.service.LocationManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/org/companies/{companyId}/locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WMS_ADMIN')")
public class LocationManagementController {

    private final LocationManagementService locationManagementService;

    @GetMapping("/{locationId}")
    public ResponseEntity<LocationDetailDto> getLocation(
            @PathVariable Long companyId,
            @PathVariable Long locationId) {
        return ResponseEntity.ok(locationManagementService.getLocation(companyId, locationId));
    }

    @PostMapping
    public ResponseEntity<LocationDetailDto> createLocation(
            @PathVariable Long companyId,
            @Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locationManagementService.createLocation(companyId, request));
    }

    @PutMapping("/{locationId}")
    public ResponseEntity<LocationDetailDto> updateLocation(
            @PathVariable Long companyId,
            @PathVariable Long locationId,
            @Valid @RequestBody UpdateLocationRequest request) {
        return ResponseEntity.ok(locationManagementService.updateLocation(companyId, locationId, request));
    }

    @PatchMapping("/{locationId}/deactivate")
    public ResponseEntity<LocationDetailDto> deactivateLocation(
            @PathVariable Long companyId,
            @PathVariable Long locationId) {
        return ResponseEntity.ok(locationManagementService.deactivateLocation(companyId, locationId));
    }
}
