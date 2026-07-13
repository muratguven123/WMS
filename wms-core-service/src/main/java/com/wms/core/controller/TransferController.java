package com.wms.core.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.core.dto.TransferRequestDto;
import com.wms.core.dto.TransferResponseDto;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.TransferValidationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/transfers")
@PreAuthorize("hasAnyRole('INVENTORY_CLERK', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferValidationService transferValidationService;

    public TransferController(TransferValidationService transferValidationService) {
        this.transferValidationService = transferValidationService;
    }

    /**
     * Depolar arası stok transfer emri oluşturur.
     *
     * <p>Akış:</p>
     * <ol>
     *   <li>Çapraz depo yetki doğrulaması yapılır</li>
     *   <li>Transfer emri oluşturulur (bu aşamada placeholder)</li>
     *   <li>Sonuç DTO'su dönülür</li>
     * </ol>
     */
    @PostMapping
    public ResponseEntity<TransferResponseDto> createTransfer(
            @Valid @RequestBody TransferRequestDto request) {

        // 1. Çapraz depo yetki doğrulaması
        transferValidationService.validateTransferAccess(request);

        // 2. Transfer emri oluştur
        // Gerçek implementasyonda: transferOrderService.create(request)
        Long transferId = 1L;

        log.info("Transfer order created — id={}, source={}, target={}, sku={}, qty={}",
                transferId, request.sourceLocationId(), request.targetLocationId(),
                request.sku(), request.quantity());

        // 3. Response
        TransferResponseDto response = new TransferResponseDto(
                transferId,
                request.sourceLocationId(),
                request.targetLocationId(),
                request.sku(),
                request.quantity(),
                "PENDING",
                OffsetDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
