package com.wms.billing.controller;

import com.wms.billing.dto.ExchangeDifferenceRequest;
import com.wms.billing.dto.ExchangeDifferenceResponse;
import com.wms.billing.mapper.InvoiceMapper;
import com.wms.billing.service.ExchangeDifferenceService;
import com.wms.billing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/invoices/{invoiceId}/exchange-difference")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class ExchangeDifferenceController {

    private final ExchangeDifferenceService exchangeDifferenceService;
    private final InvoiceService invoiceService;
    private final InvoiceMapper invoiceMapper;

    @PostMapping
    public ResponseEntity<ExchangeDifferenceResponse> calculate(
            @PathVariable Long invoiceId,
            @Valid @RequestBody ExchangeDifferenceRequest request) {
        invoiceService.requireInvoiceForTenant(invoiceId);

        var log = exchangeDifferenceService.calculateAndLogExchangeDifference(
                invoiceId, request.paidAmountOriginal(), request.rateAtPayment());

        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceMapper.toResponse(log));
    }
}
