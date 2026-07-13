package com.wms.localization.controller;

import com.wms.localization.dto.address.AddressDto;
import com.wms.localization.dto.address.AddressResponse;
import com.wms.localization.dto.address.CountryAddressTemplateDto;
import com.wms.localization.service.address.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * Dinamik adres REST API'si.
 *
 * <p>POST/PUT isteklerinde {@link com.wms.localization.service.address.AddressValidationService}
 * ülke şablonuna göre {@code addressDetails} doğrulaması yapar.</p>
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * Bir ülke için dinamik adres şablonunu döner (sıralı alan listesi).
     * Şablon tanımlı değilse boş liste döner (404 değil — bu bir hata durumu değildir).
     */
    @GetMapping("/templates/{countryId}")
    public ResponseEntity<List<CountryAddressTemplateDto>> getTemplate(@PathVariable Long countryId) {
        return ResponseEntity.ok(addressService.getTemplateByCountry(countryId));
    }

    /**
     * Kayıtlı adresleri sayfalı listeler. {@code countryId} opsiyonel filtre.
     */
    @GetMapping
    public ResponseEntity<Page<AddressResponse>> list(
            @RequestParam(required = false) Long countryId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(addressService.list(countryId, pageable));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@Valid @RequestBody AddressDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AddressDto dto) {
        return ResponseEntity.ok(addressService.update(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AddressResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(addressService.getById(id));
    }
}
