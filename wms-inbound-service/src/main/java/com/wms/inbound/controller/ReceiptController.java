package com.wms.inbound.controller;

import com.wms.inbound.dto.ReceiptQcRequest;
import com.wms.inbound.dto.ReceiptResponse;
import com.wms.inbound.dto.StartReceiptRequest;
import com.wms.inbound.entity.enums.ReceiptStatus;
import com.wms.inbound.service.ReceiptService;
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
@RequestMapping("/api/inbound/receipts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INBOUND_CLERK', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    public ResponseEntity<Page<ReceiptResponse>> listReceipts(
            @RequestParam(required = false) ReceiptStatus status,
            @PageableDefault(size = 20, sort = "receivedAt") Pageable pageable) {
        return ResponseEntity.ok(receiptService.listReceipts(status, pageable));
    }

    @PostMapping
    public ResponseEntity<ReceiptResponse> startReceipt(@Valid @RequestBody StartReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.startReceipt(request));
    }

    @PostMapping("/{receiptId}/qc")
    public ResponseEntity<ReceiptResponse> inputQcResults(
            @PathVariable Long receiptId,
            @Valid @RequestBody ReceiptQcRequest request) {
        return ResponseEntity.ok(receiptService.inputQcResults(receiptId, request));
    }

    @PostMapping("/{receiptId}/approve")
    public ResponseEntity<ReceiptResponse> approveReceipt(@PathVariable Long receiptId) {
        return ResponseEntity.ok(receiptService.approveReceipt(receiptId));
    }
}
