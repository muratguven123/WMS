package com.wms.outbound.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.outbound.dto.CloseBoxRequest;
import com.wms.outbound.dto.CloseBoxResponse;
import com.wms.outbound.dto.PackingVerifyRequest;
import com.wms.outbound.dto.PackingVerifyResponse;
import com.wms.outbound.service.PackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/packing")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PACKER', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class PackingController {

    private final PackingService packingService;

    @PostMapping("/verify")
    public ResponseEntity<PackingVerifyResponse> verifyPackingItem(
            @Valid @RequestBody PackingVerifyRequest request) {
        log.info("REST request to verify packing item: {}", request);
        PackingVerifyResponse response = packingService.verifyPackingItem(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/close-box")
    public ResponseEntity<CloseBoxResponse> closeBox(
            @Valid @RequestBody CloseBoxRequest request) {
        log.info("REST request to close box for picking list: {}", request.pickingListId());
        CloseBoxResponse response = packingService.closeBox(request.pickingListId());
        return ResponseEntity.ok(response);
    }
}
