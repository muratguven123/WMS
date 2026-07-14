package com.wms.core.controller;

import com.wms.core.dto.company.CompanyAdminDto;
import com.wms.core.dto.company.CompanyUsageDto;
import com.wms.core.dto.company.CreateCompanyRequest;
import com.wms.core.dto.company.OrganizationOptionDto;
import com.wms.core.dto.company.UpdateCompanyRequest;
import com.wms.core.service.CompanyAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Firma (Company) yönetimi admin API'si.
 *
 * <pre>
 * GET    /api/admin/org/organizations              → aktif organizasyon listesi (form)
 * GET    /api/admin/org/companies                  → pasifler dahil firma listesi
 * POST   /api/admin/org/companies                  → yeni firma
 * PUT    /api/admin/org/companies/{id}             → firma güncelleme
 * DELETE /api/admin/org/companies/{id}             → pasifleştirme (soft delete)
 * POST   /api/admin/org/companies/{id}/reactivate  → yeniden aktifleştirme
 * GET    /api/admin/org/companies/{id}/usage       → kullanım özeti
 * </pre>
 *
 * <p>Tüm endpoint'ler {@code WMS_ADMIN} rolü gerektirir.</p>
 */
@RestController
@RequestMapping("/api/admin/org")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WMS_ADMIN')")
public class CompanyAdminController {

    private final CompanyAdminService companyAdminService;

    /** Firma oluşturma formundaki organizasyon seçenekleri. */
    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationOptionDto>> listOrganizations() {
        return ResponseEntity.ok(companyAdminService.listOrganizations());
    }

    /** Pasif kayıtlar dahil tüm firmaları depo sayacıyla listeler. */
    @GetMapping("/companies")
    public ResponseEntity<List<CompanyAdminDto>> listCompanies() {
        return ResponseEntity.ok(companyAdminService.listCompanies());
    }

    /** Yeni firma oluşturur. Vergi no mükerrerse 409. */
    @PostMapping("/companies")
    public ResponseEntity<CompanyAdminDto> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminService.createCompany(request));
    }

    /** Firma adı / vergi no / vergi dairesini günceller. */
    @PutMapping("/companies/{companyId}")
    public ResponseEntity<CompanyAdminDto> updateCompany(
            @PathVariable Long companyId,
            @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyAdminService.updateCompany(companyId, request));
    }

    /**
     * Firmayı pasifleştirir (soft delete). Aktif depo veya kullanıcı yetkisi
     * varsa 409 Conflict. UI öncesinde {@code /usage} ile onay göstermelidir.
     */
    @DeleteMapping("/companies/{companyId}")
    public ResponseEntity<Void> deactivateCompany(@PathVariable Long companyId) {
        companyAdminService.deactivateCompany(companyId);
        return ResponseEntity.noContent().build();
    }

    /** Pasif firmayı yeniden aktifleştirir. */
    @PostMapping("/companies/{companyId}/reactivate")
    public ResponseEntity<CompanyAdminDto> reactivateCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(companyAdminService.reactivateCompany(companyId));
    }

    /** Firma kullanım özeti (pasifleştirme onay diyaloğu). */
    @GetMapping("/companies/{companyId}/usage")
    public ResponseEntity<CompanyUsageDto> getCompanyUsage(@PathVariable Long companyId) {
        return ResponseEntity.ok(companyAdminService.getCompanyUsage(companyId));
    }
}
