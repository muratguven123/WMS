package com.wms.integration.api;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.integration.service.ErpReceiptIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Inbound servisinden gelen mal kabul onaylarını ERP entegrasyon kuyruğuna alır.
 */
@RestController
@RequestMapping("/api/integration/erp")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INTEGRATION_ADMIN', 'WMS_ADMIN')")
public class ErpReceiptIntegrationController {

    private final ErpReceiptIntegrationService erpReceiptIntegrationService;

    @PostMapping("/receipts")
    public ResponseEntity<Map<String, String>> receiveReceiptApproval(@RequestBody String payload) {
        erpReceiptIntegrationService.enqueueReceipt(payload);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "Receipt approval enqueued for ERP sync via Outbox"
        ));
    }
}
