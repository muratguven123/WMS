package com.wms.integration.api;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.integration.adapter.dto.MovementDto;
import com.wms.integration.service.InventoryMovementIntegrationService;
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
}
