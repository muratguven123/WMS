package com.wms.core.controller;

import com.wms.core.dto.org.CompanySummaryDto;
import com.wms.core.dto.org.LocationSummaryDto;
import com.wms.core.dto.org.RegionSummaryDto;
import com.wms.core.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/companies")
    public ResponseEntity<List<CompanySummaryDto>> listCompanies() {
        return ResponseEntity.ok(organizationService.listAccessibleCompanies());
    }

    @GetMapping("/companies/{companyId}/locations")
    public ResponseEntity<List<LocationSummaryDto>> listLocations(@PathVariable Long companyId) {
        return ResponseEntity.ok(organizationService.listAccessibleLocations(companyId));
    }

    @GetMapping("/regions")
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ResponseEntity<List<RegionSummaryDto>> listRegions(@RequestParam Long countryId) {
        return ResponseEntity.ok(organizationService.listRegionsByCountry(countryId));
    }
}
