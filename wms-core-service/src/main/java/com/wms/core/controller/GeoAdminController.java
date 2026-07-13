package com.wms.core.controller;

import com.wms.core.dto.address.CityDto;
import com.wms.core.dto.address.DistrictDto;
import com.wms.core.dto.address.NeighborhoodDto;
import com.wms.core.dto.address.StateProvinceDto;
import com.wms.core.dto.geo.*;
import com.wms.core.service.GeoAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * İş İsteri 18 — Ülke ve idari birim yönetimi admin API'si.
 *
 * <pre>
 * GET    /api/admin/geo/countries                     → pasifler dahil ülke listesi
 * POST   /api/admin/geo/countries                     → yeni ülke
 * PUT    /api/admin/geo/countries/{id}                → ülke adı güncelleme
 * DELETE /api/admin/geo/countries/{id}                → pasifleştirme (soft delete)
 * POST   /api/admin/geo/countries/{id}/reactivate     → yeniden aktifleştirme
 * GET    /api/admin/geo/countries/{id}/usage          → cross-service kullanım özeti
 * POST   /api/admin/geo/countries/{id}/states         → eyalet ekleme
 * POST   /api/admin/geo/countries/{id}/states/import  → CSV toplu eyalet import
 * PUT    /api/admin/geo/states/{id}                   → eyalet güncelleme
 * DELETE /api/admin/geo/states/{id}                   → eyalet pasifleştirme
 * POST   /api/admin/geo/countries/{id}/cities         → şehir ekleme (eyaletli/eyaletsiz)
 * PUT    /api/admin/geo/cities/{id}/assign-state      → şehri eyalete bağlama
 * POST   /api/admin/geo/cities/{id}/districts         → ilçe ekleme
 * POST   /api/admin/geo/districts/{id}/neighborhoods  → mahalle ekleme
 * </pre>
 *
 * <p>Okuma (cascade dropdown) endpoint'leri {@link AddressController}'da kalır
 * ve değişmez. Tüm admin endpoint'leri {@code WMS_ADMIN} rolü gerektirir.</p>
 */
@RestController
@RequestMapping("/api/admin/geo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WMS_ADMIN')")
public class GeoAdminController {

    private final GeoAdminService geoAdminService;

    // ── Ülke ──────────────────────────────────────────────────────────────

    /** Pasif kayıtlar dahil tüm ülkeleri sayaçlarla listeler (admin ekranı). */
    @GetMapping("/countries")
    public ResponseEntity<List<CountryAdminDto>> listCountries() {
        return ResponseEntity.ok(geoAdminService.listCountries());
    }

    /** Yeni ülke oluşturur. isoCode uppercase'e normalize edilir. */
    @PostMapping("/countries")
    public ResponseEntity<CountryAdminDto> createCountry(
            @Valid @RequestBody CreateCountryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(geoAdminService.createCountry(request));
    }

    /** Ülke adını günceller (isoCode değiştirilemez). */
    @PutMapping("/countries/{countryId}")
    public ResponseEntity<CountryAdminDto> updateCountry(
            @PathVariable Long countryId,
            @Valid @RequestBody UpdateCountryRequest request) {
        return ResponseEntity.ok(geoAdminService.updateCountry(countryId, request));
    }

    /**
     * Ülkeyi pasifleştirir (soft delete). UI, öncesinde {@code /usage} ile
     * kullanım onayını göstermekle yükümlüdür.
     */
    @DeleteMapping("/countries/{countryId}")
    public ResponseEntity<Void> deactivateCountry(@PathVariable Long countryId) {
        geoAdminService.deactivateCountry(countryId);
        return ResponseEntity.noContent().build();
    }

    /** Pasif ülkeyi yeniden aktifleştirir. */
    @PostMapping("/countries/{countryId}/reactivate")
    public ResponseEntity<CountryAdminDto> reactivateCountry(@PathVariable Long countryId) {
        return ResponseEntity.ok(geoAdminService.reactivateCountry(countryId));
    }

    /** Ülkenin localization-service'teki kullanım özeti (best-effort). */
    @GetMapping("/countries/{countryId}/usage")
    public ResponseEntity<CountryUsageDto> getCountryUsage(@PathVariable Long countryId) {
        return ResponseEntity.ok(geoAdminService.getCountryUsage(countryId));
    }

    // ── Eyalet ────────────────────────────────────────────────────────────

    /** Ülkeye eyalet ekler. */
    @PostMapping("/countries/{countryId}/states")
    public ResponseEntity<StateProvinceDto> addState(
            @PathVariable Long countryId,
            @Valid @RequestBody UpsertStateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(geoAdminService.addState(countryId, request));
    }

    /**
     * CSV toplu eyalet import'u. Body: text/csv — her satır {@code name[,code]}.
     * Örn: 81 ilin tek seferde yüklenmesi.
     */
    @PostMapping(value = "/countries/{countryId}/states/import", consumes = {"text/csv", "text/plain"})
    public ResponseEntity<StateImportResultDto> importStates(
            @PathVariable Long countryId,
            @RequestBody String csvContent) {
        return ResponseEntity.ok(geoAdminService.importStates(countryId, csvContent));
    }

    /** Eyalet adı/kodunu günceller. */
    @PutMapping("/states/{stateId}")
    public ResponseEntity<StateProvinceDto> updateState(
            @PathVariable Long stateId,
            @Valid @RequestBody UpsertStateRequest request) {
        return ResponseEntity.ok(geoAdminService.updateState(stateId, request));
    }

    /** Eyaleti pasifleştirir — aktif şehri varsa 409 döner. */
    @DeleteMapping("/states/{stateId}")
    public ResponseEntity<Void> deactivateState(@PathVariable Long stateId) {
        geoAdminService.deactivateState(stateId);
        return ResponseEntity.noContent().build();
    }

    // ── Şehir ─────────────────────────────────────────────────────────────

    /** Şehir ekler — stateProvinceId opsiyonel (eyaletsiz model desteklenir). */
    @PostMapping("/countries/{countryId}/cities")
    public ResponseEntity<CityDto> addCity(
            @PathVariable Long countryId,
            @Valid @RequestBody CreateCityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(geoAdminService.addCity(countryId, request));
    }

    /** Mevcut şehri eyalete bağlar (TR eyalet geçiş senaryosu). */
    @PutMapping("/cities/{cityId}/assign-state")
    public ResponseEntity<CityDto> assignCityToState(
            @PathVariable Long cityId,
            @Valid @RequestBody AssignStateRequest request) {
        return ResponseEntity.ok(geoAdminService.assignCityToState(cityId, request));
    }

    // ── İlçe / Mahalle ────────────────────────────────────────────────────

    /** Şehre ilçe ekler. */
    @PostMapping("/cities/{cityId}/districts")
    public ResponseEntity<DistrictDto> addDistrict(
            @PathVariable Long cityId,
            @Valid @RequestBody CreateDistrictRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(geoAdminService.addDistrict(cityId, request));
    }

    /** İlçeye mahalle ekler. */
    @PostMapping("/districts/{districtId}/neighborhoods")
    public ResponseEntity<NeighborhoodDto> addNeighborhood(
            @PathVariable Long districtId,
            @Valid @RequestBody CreateNeighborhoodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(geoAdminService.addNeighborhood(districtId, request));
    }
}
