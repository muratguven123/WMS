package com.wms.localization.controller;

import com.wms.localization.dto.address.*;
import com.wms.localization.service.address.AddressTemplateAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * İş İsteri 17 — Adres şablonu ve alan kataloğu admin API'si.
 *
 * <p>Okuma endpoint'leri authenticated kullanıcıya açık;
 * yazma işlemleri {@code ADDRESS_CONFIG_ADMIN}, {@code LOCALIZATION_ADMIN}
 * veya {@code WMS_ADMIN} rolü gerektirir.</p>
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressTemplateAdminController {

    private final AddressTemplateAdminService adminService;

    // ── Alan kataloğu ─────────────────────────────────────────────────────

    @GetMapping("/template-fields")
    public ResponseEntity<List<AddressTemplateFieldDto>> listFields() {
        return ResponseEntity.ok(adminService.listFields());
    }

    @PostMapping("/template-fields")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<AddressTemplateFieldDto> createField(
            @Valid @RequestBody CreateAddressTemplateFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createField(request));
    }

    @PutMapping("/template-fields/{fieldId}")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<AddressTemplateFieldDto> updateField(
            @PathVariable Long fieldId,
            @Valid @RequestBody UpdateAddressTemplateFieldRequest request) {
        return ResponseEntity.ok(adminService.updateField(fieldId, request));
    }

    @DeleteMapping("/template-fields/{fieldId}")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<Void> deleteField(@PathVariable Long fieldId) {
        adminService.deleteField(fieldId);
        return ResponseEntity.noContent().build();
    }

    // ── Ülke şablonu ──────────────────────────────────────────────────────

    @PostMapping("/templates/{countryId}/fields")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<TemplateFieldMutationResponse> addTemplateField(
            @PathVariable Long countryId,
            @Valid @RequestBody AddCountryTemplateFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.addTemplateField(countryId, request));
    }

    @PutMapping("/templates/{countryId}/fields/{templateId}")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<TemplateFieldMutationResponse> updateTemplateField(
            @PathVariable Long countryId,
            @PathVariable Long templateId,
            @Valid @RequestBody UpdateCountryTemplateFieldRequest request) {
        return ResponseEntity.ok(adminService.updateTemplateField(countryId, templateId, request));
    }

    @PutMapping("/templates/{countryId}/reorder")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<List<CountryAddressTemplateDto>> reorderTemplate(
            @PathVariable Long countryId,
            @Valid @RequestBody ReorderCountryTemplateRequest request) {
        return ResponseEntity.ok(adminService.reorderTemplate(countryId, request));
    }

    @DeleteMapping("/templates/{countryId}/fields/{templateId}")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<Void> removeTemplateField(
            @PathVariable Long countryId,
            @PathVariable Long templateId) {
        adminService.removeTemplateField(countryId, templateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/templates/{countryId}/copy-from/{sourceCountryId}")
    @PreAuthorize("hasAnyRole('ADDRESS_CONFIG_ADMIN', 'LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<CopyTemplateResultDto> copyFrom(
            @PathVariable Long countryId,
            @PathVariable Long sourceCountryId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.copyFrom(countryId, sourceCountryId));
    }
}
