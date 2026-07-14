package com.wms.finance.controller;

import com.wms.finance.dto.ErpTaxImportRequest;
import com.wms.finance.dto.ExchangeDifferenceTaxRequest;
import com.wms.finance.dto.TaxCalculateRequest;
import com.wms.finance.dto.TaxCalculateResponse;
import com.wms.finance.dto.TaxRateListItemDto;
import com.wms.finance.dto.TaxRateUpdateRequest;
import com.wms.finance.dto.TaxRateVersionResult;
import com.wms.finance.service.ErpTaxImportService;
import com.wms.finance.service.TaxQueryService;
import com.wms.finance.service.TaxRateVersioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class TaxRateController {

    private final TaxRateVersioningService versioningService;
    private final TaxQueryService taxQueryService;
    private final ErpTaxImportService erpTaxImportService;

    @GetMapping("/api/taxes/rates")
    public ResponseEntity<Page<TaxRateListItemDto>> listRates(
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String taxTypeCode,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(taxQueryService.listRates(countryId, locationId, taxTypeCode, active, pageable));
    }

    @PostMapping("/api/taxes/rates/update")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<TaxRateVersionResult> updateRate(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody TaxRateUpdateRequest request) {

        log.info("[API] Tax rate update → taxRateId={}, effectiveDate={}, userId={}",
                request.taxRateId(), request.effectiveDate(), userId);

        return ResponseEntity.ok(versioningService.updateRate(request, userId));
    }

    @PostMapping("/api/taxes/calculate")
    public ResponseEntity<TaxCalculateResponse> calculate(@Valid @RequestBody TaxCalculateRequest request) {
        return ResponseEntity.ok(taxQueryService.calculate(request));
    }

    @PostMapping("/api/taxes/exchange-difference")
    public ResponseEntity<TaxCalculateResponse> calculateExchangeDifferenceTax(
            @Valid @RequestBody ExchangeDifferenceTaxRequest request) {
        return ResponseEntity.ok(taxQueryService.calculateExchangeDifferenceTax(request));
    }

    @PostMapping("/api/taxes/rates/from-erp")
    public ResponseEntity<TaxRateListItemDto> importFromErp(@Valid @RequestBody ErpTaxImportRequest request) {
        var saved = erpTaxImportService.importRate(request);
        return ResponseEntity.ok(new TaxRateListItemDto(
                saved.getId(),
                saved.getTaxType().getCode(),
                saved.getCountryId(),
                saved.getLocationId(),
                saved.getCustomerId(),
                saved.getProductType(),
                saved.getOperationType(),
                saved.getRate(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.isActive(),
                saved.getCreatedAt()));
    }
}
