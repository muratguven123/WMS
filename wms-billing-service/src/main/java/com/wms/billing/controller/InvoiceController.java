package com.wms.billing.controller;

import com.wms.billing.domain.enums.InvoiceStatus;
import com.wms.billing.dto.CalculateInvoiceRequest;
import com.wms.billing.dto.CreateInvoiceRequest;
import com.wms.billing.dto.InvoiceResponse;
import com.wms.billing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/calculate")
    public ResponseEntity<InvoiceResponse> calculate(@Valid @RequestBody CalculateInvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.calculatePreview(request));
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceResponse response = invoiceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<InvoiceResponse>> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) InvoiceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(invoiceService.list(customerId, status, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvoiceResponse> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.updateDraft(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<InvoiceResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.approve(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<InvoiceResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.cancel(id));
    }
}
