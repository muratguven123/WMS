package com.wms.outbound.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.outbound.dto.CarrierResponseDto;
import com.wms.outbound.dto.CreateShipmentRequest;
import com.wms.outbound.dto.RequestShippingLabelRequest;
import com.wms.outbound.dto.ShipmentSummaryDto;
import com.wms.outbound.dto.VerifyLoadRequest;
import com.wms.outbound.dto.VerifyLoadResponse;
import com.wms.outbound.entity.Shipment;
import com.wms.outbound.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SHIPPING_CLERK', 'WAREHOUSE_MANAGER', 'WMS_ADMIN')")
public class ShippingController {

    private final ShipmentService shipmentService;

    @GetMapping("/shipments")
    public ResponseEntity<Page<ShipmentSummaryDto>> listShipments(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(shipmentService.listShipments(pageable));
    }

    @PostMapping("/shipments")
    public ResponseEntity<Shipment> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        log.info("REST request to create shipment: {}", request.shipmentNumber());
        Shipment shipment = shipmentService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(shipment);
    }

    @PostMapping("/{shipmentId}/label")
    public ResponseEntity<CarrierResponseDto> requestShippingLabel(
            @PathVariable Long shipmentId,
            @Valid @RequestBody RequestShippingLabelRequest request) {
        log.info("REST request to request shipping label for shipment: {}", shipmentId);
        CarrierResponseDto response = shipmentService.requestShippingLabel(shipmentId, request.address());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-load")
    public ResponseEntity<VerifyLoadResponse> verifyLoad(@Valid @RequestBody VerifyLoadRequest request) {
        log.info("REST request to verify load: {}", request);
        VerifyLoadResponse response = shipmentService.verifyLoad(request.shipmentId(), request.boxSsccNumber());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{shipmentId}/dispatch")
    public ResponseEntity<Shipment> dispatch(@PathVariable Long shipmentId) {
        log.info("REST request to dispatch shipment: {}", shipmentId);
        Shipment shipment = shipmentService.dispatch(shipmentId);
        return ResponseEntity.ok(shipment);
    }
}
