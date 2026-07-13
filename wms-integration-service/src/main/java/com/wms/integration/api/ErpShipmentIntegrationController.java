package com.wms.integration.api;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.integration.service.ErpShipmentIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Outbound servisinden gelen sevkiyat çıkışlarını ERP entegrasyon kuyruğuna alır.
 */
@RestController
@RequestMapping("/api/integration/erp")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INTEGRATION_ADMIN', 'WMS_ADMIN')")
public class ErpShipmentIntegrationController {

    private final ErpShipmentIntegrationService erpShipmentIntegrationService;

    @PostMapping("/shipments")
    public ResponseEntity<Map<String, String>> receiveShipmentDispatch(@RequestBody String payload) {
        erpShipmentIntegrationService.enqueueShipment(payload);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "QUEUED",
                "message", "Shipment dispatch enqueued for ERP sync via Outbox"
        ));
    }
}
