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
}
