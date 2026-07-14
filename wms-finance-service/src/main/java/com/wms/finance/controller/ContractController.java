package com.wms.finance.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.finance.dto.ContractDto;
import com.wms.finance.dto.CreateContractRequest;
import com.wms.finance.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wms.finance.dto.ContractFixedRateDto;
import com.wms.finance.dto.ContractFixedRateRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/contracts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'WMS_ADMIN')")
public class ContractController {

    private final ContractService contractService;

    @PostMapping
    public ResponseEntity<ContractDto> createContract(
            @Valid @RequestBody CreateContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(request));
    }

    @GetMapping("/{contractId}/fixed-rates")
    public ResponseEntity<List<ContractFixedRateDto>> getFixedRates(
            @PathVariable Long contractId) {
        return ResponseEntity.ok(contractService.getFixedRates(contractId));
    }

    @PostMapping("/{contractId}/fixed-rates")
    public ResponseEntity<ContractFixedRateDto> addFixedRate(
            @PathVariable Long contractId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ContractFixedRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.addFixedRate(contractId, request, userId));
    }

    @PutMapping("/{contractId}/fixed-rates/{rateId}")
    public ResponseEntity<ContractFixedRateDto> updateFixedRate(
            @PathVariable Long contractId,
            @PathVariable Long rateId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ContractFixedRateRequest request) {
        return ResponseEntity.ok(contractService.updateFixedRate(contractId, rateId, request, userId));
    }

    @DeleteMapping("/{contractId}/fixed-rates/{rateId}")
    public ResponseEntity<Void> deleteFixedRate(
            @PathVariable Long contractId,
            @PathVariable Long rateId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        contractService.deleteFixedRate(contractId, rateId, userId);
        return ResponseEntity.noContent().build();
    }
}
