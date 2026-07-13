package com.wms.finance.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.finance.dto.CreateFinancialTransactionRequest;
import com.wms.finance.dto.FinancialTransactionDto;
import com.wms.finance.service.FinancialTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class FinancialTransactionController {

    private final FinancialTransactionService transactionService;

    @PostMapping
    public ResponseEntity<FinancialTransactionDto> record(
            @Valid @RequestBody CreateFinancialTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.recordTransaction(request));
    }
}
