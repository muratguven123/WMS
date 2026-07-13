package com.wms.core.controller;

import com.wms.core.dto.address.*;
import com.wms.core.service.AddressQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hiyerarşik adres cascade dropdown endpointleri.
 *
 * <pre>
 * GET /api/address/countries
 * GET /api/address/states?countryId={id}
 * GET /api/address/cities?countryId={id}[&stateId={id}]
 * GET /api/address/districts?cityId={id}
 * GET /api/address/neighborhoods?districtId={id}
 * </pre>
 *
 * Tüm endpointler yalnızca {@code isActive = true} kayıtları döner.
 * Yazma işlemleri (admin CRUD) {@code AddressAdminController}'da yönetilir.
 */
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressQueryService addressQueryService;

    /**
     * Tüm aktif ülkeleri listeler.
     * Cascade dropdown başlangıç noktası.
     *
     * @return [{id, isoCode, name}]
     */
    @GetMapping("/countries")
    public ResponseEntity<List<CountryDto>> listCountries() {
        return ResponseEntity.ok(addressQueryService.listCountries());
    }

    /**
     * Verilen ülkeye ait aktif eyalet / illeri listeler.
     *
     * @param countryId ülke Long'si (zorunlu)
     * @return [{id, name, code}]
     */
    @GetMapping("/states")
    public ResponseEntity<List<StateProvinceDto>> listStates(
            @RequestParam Long countryId) {
        return ResponseEntity.ok(addressQueryService.listStates(countryId));
    }

    /**
     * Eyalete bağlı (veya eyalet yoksa ülkeye doğrudan bağlı) aktif şehirleri listeler.
     *
     * <ul>
     *   <li>{@code stateId} verilmişse → eyalete ait şehirler</li>
     *   <li>{@code stateId} verilmemişse → ülkeye ait şehirler</li>
     * </ul>
     *
     * @param countryId ülke Long'si (zorunlu)
     * @param stateId   eyalet Long'si (opsiyonel)
     * @return [{id, name}]
     */
    @GetMapping("/cities")
    public ResponseEntity<List<CityDto>> listCities(
            @RequestParam Long countryId,
            @RequestParam(required = false) Long stateId) {
        return ResponseEntity.ok(addressQueryService.listCities(countryId, stateId));
    }

    /**
     * Verilen şehre ait aktif ilçeleri listeler.
     *
     * @param cityId şehir Long'si (zorunlu)
     * @return [{id, name}]
     */
    @GetMapping("/districts")
    public ResponseEntity<List<DistrictDto>> listDistricts(
            @RequestParam Long cityId) {
        return ResponseEntity.ok(addressQueryService.listDistricts(cityId));
    }

    /**
     * Verilen ilçeye ait aktif mahalleleri posta kodlarıyla listeler.
     * UI Posta Kodu alanı {@code zipCode} ile otomatik doldurulur;
     * kullanıcı gerekirse override edebilir (İş Kuralı 4).
     *
     * @param districtId ilçe Long'si (zorunlu)
     * @return [{id, name, zipCode}]
     */
    @GetMapping("/neighborhoods")
    public ResponseEntity<List<NeighborhoodDto>> listNeighborhoods(
            @RequestParam Long districtId) {
        return ResponseEntity.ok(addressQueryService.listNeighborhoods(districtId));
    }
}
