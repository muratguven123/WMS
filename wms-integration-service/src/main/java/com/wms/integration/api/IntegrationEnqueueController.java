package com.wms.integration.api;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.integration.adapter.dto.*;
import com.wms.integration.service.InventoryMovementIntegrationService;
import com.wms.integration.service.ErpScenarioIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Entegrasyon kuyruğuna iş ekleme API'si (Outbox Pattern demo).
 */
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INTEGRATION_ADMIN', 'WMS_ADMIN')")
public class IntegrationEnqueueController {

    private final InventoryMovementIntegrationService movementIntegrationService;
    private final ErpScenarioIntegrationService erpScenarioIntegrationService;

    @PostMapping("/movements")
    public ResponseEntity<Map<String, String>> enqueueMovement(
            @RequestParam Long locationId,
            @Valid @RequestBody MovementDto movement) {

        movementIntegrationService.enqueueMovement(movement, locationId);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "Movement enqueued for ERP sync via Outbox"
        ));
    }

    @PostMapping("/customers")
    public ResponseEntity<Map<String, String>> enqueueCustomer(
            @Valid @RequestBody CustomerAccountDto customer) {

        erpScenarioIntegrationService.enqueueCustomerAccount(customer);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "CustomerAccount enqueued for ERP sync via Outbox"
        ));
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<Map<String, String>> enqueuePurchaseOrder(
            @Valid @RequestBody PurchaseOrderDto purchaseOrder) {

        erpScenarioIntegrationService.enqueuePurchaseOrder(purchaseOrder);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "PurchaseOrder enqueued for ERP sync via Outbox"
        ));
    }

    @PostMapping("/sales-orders")
    public ResponseEntity<Map<String, String>> enqueueSalesOrder(
            @Valid @RequestBody SalesOrderDto salesOrder) {

        erpScenarioIntegrationService.enqueueSalesOrder(salesOrder);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "SalesOrder enqueued for ERP sync via Outbox"
        ));
    }

    @PostMapping("/returns")
    public ResponseEntity<Map<String, String>> enqueueReturn(
            @Valid @RequestBody ReturnNoticeDto returnNotice) {

        erpScenarioIntegrationService.enqueueReturnNotice(returnNotice);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "ReturnNotice enqueued for ERP sync via Outbox"
        ));
    }

    @PostMapping("/counts")
    public ResponseEntity<Map<String, String>> enqueueCount(
            @Valid @RequestBody CountResultDto countResult) {

        erpScenarioIntegrationService.enqueueCountResult(countResult);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "CountResult enqueued for ERP sync via Outbox"
        ));
    }

    @PostMapping("/vouchers")
    public ResponseEntity<Map<String, String>> enqueueVoucher(
            @Valid @RequestBody AccountingVoucherDto voucher) {

        erpScenarioIntegrationService.enqueueAccountingVoucher(voucher);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "AccountingVoucher enqueued for ERP sync via Outbox"
        ));
    }
}

