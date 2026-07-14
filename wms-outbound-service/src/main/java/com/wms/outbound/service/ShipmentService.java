package com.wms.outbound.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.dto.CarrierResponseDto;
import com.wms.outbound.dto.CreateShipmentRequest;
import com.wms.outbound.dto.InventoryIssueRequest;
import com.wms.outbound.dto.ShipmentDispatchedEvent;
import com.wms.outbound.dto.ShipmentSummaryDto;
import com.wms.outbound.dto.ShippingAddressDto;
import com.wms.outbound.dto.VerifyLoadResponse;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderItem;
import com.wms.outbound.entity.OutboxMessage;
import com.wms.outbound.entity.Shipment;
import com.wms.outbound.entity.ShipmentItem;
import com.wms.outbound.entity.enums.OutboundOrderStatus;
import com.wms.outbound.entity.enums.OutboxStatus;
import com.wms.outbound.entity.enums.ShipmentItemStatus;
import com.wms.outbound.entity.enums.ShipmentStatus;
import com.wms.outbound.exception.BusinessException;
import com.wms.outbound.integration.CoreServiceClient;
import com.wms.outbound.integration.InventoryServiceClient;
import com.wms.outbound.integration.carrier.CarrierIntegrationService;
import com.wms.outbound.repository.OutboundOrderRepository;
import com.wms.outbound.repository.OutboxMessageRepository;
import com.wms.outbound.repository.ShipmentItemRepository;
import com.wms.outbound.repository.ShipmentRepository;
import com.wms.outbound.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final InventoryServiceClient inventoryServiceClient;
    private final CarrierIntegrationService carrierIntegrationService;
    private final ObjectMapper objectMapper;
    private final CoreServiceClient coreServiceClient;

    @Transactional(readOnly = true)
    public Page<ShipmentSummaryDto> listShipments(Pageable pageable) {
        Long companyId = TenantScopeGuard.requireCompanyId();
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();
        return shipmentRepository
                .findByCompanyIdAndWarehouseLocationId(companyId, warehouseLocationId, pageable)
                .map(s -> new ShipmentSummaryDto(s.getId(), s.getShipmentNumber(), s.getStatus()));
    }

    @Transactional
    public Shipment createShipment(CreateShipmentRequest request) {
        TenantScopeGuard.assertMatchesContext(request.warehouseLocationId());
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();
        Long companyId = TenantScopeGuard.requireCompanyId();

        coreServiceClient.enforceWorkflowStep("OUTBOUND", "SHIPPING", null, "SHIPMENT");

        if (shipmentRepository.findByShipmentNumber(request.shipmentNumber()).isPresent()) {
            throw new BusinessException(
                    "Shipment number already exists: " + request.shipmentNumber(), HttpStatus.BAD_REQUEST);
        }

        Shipment shipment = Shipment.builder()
                .shipmentNumber(request.shipmentNumber())
                .companyId(companyId)
                .warehouseLocationId(warehouseLocationId)
                .carrierCode(request.carrierCode())
                .status(ShipmentStatus.PENDING)
                .totalBoxes(request.boxes().size())
                .totalWeight(request.totalWeight())
                .items(new ArrayList<>())
                .build();

        for (var box : request.boxes()) {
            ShipmentItem item = ShipmentItem.builder()
                    .shipment(shipment)
                    .outboundOrderId(box.outboundOrderId())
                    .boxSsccNumber(box.boxSsccNumber())
                    .status(ShipmentItemStatus.STAGED)
                    .build();
            shipment.getItems().add(item);
        }

        Shipment saved = shipmentRepository.save(shipment);
        log.info("Created shipment {} with {} boxes", saved.getShipmentNumber(), saved.getTotalBoxes());
        return saved;
    }

    @Transactional
    public CarrierResponseDto requestShippingLabel(Long shipmentId, ShippingAddressDto address) {
        Shipment shipment = shipmentRepository.findWithDetailsById(shipmentId)
                .orElseThrow(() -> new BusinessException("Shipment not found: " + shipmentId, HttpStatus.NOT_FOUND));

        if (shipment.getStatus() == ShipmentStatus.DISPATCHED) {
            throw new BusinessException("Cannot request label for a dispatched shipment", HttpStatus.BAD_REQUEST);
        }
        if (shipment.getCarrierCode() == null || shipment.getCarrierCode().isBlank()) {
            throw new BusinessException("Carrier code is required before requesting a shipping label",
                    HttpStatus.BAD_REQUEST);
        }

        CarrierResponseDto response = carrierIntegrationService.requestShippingLabel(shipment, address);
        if (response.isSuccess() && response.getTrackingNumber() != null) {
            shipment.setTrackingNumber(response.getTrackingNumber());
            shipmentRepository.save(shipment);
        }
        return response;
    }

    @Transactional
    public VerifyLoadResponse verifyLoad(Long shipmentId, String boxSsccNumber) {
        log.info("Verifying box SSCC: {} for shipment: {}", boxSsccNumber, shipmentId);

        Shipment shipment = shipmentRepository.findWithDetailsById(shipmentId)
                .orElseThrow(() -> new BusinessException("Shipment not found: " + shipmentId, HttpStatus.NOT_FOUND));

        if (shipment.getStatus() == ShipmentStatus.DISPATCHED) {
            throw new BusinessException("Shipment has already been dispatched!", HttpStatus.BAD_REQUEST);
        }

        ShipmentItem match = shipment.getItems().stream()
                .filter(item -> item.getBoxSsccNumber().equals(boxSsccNumber))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "SSCC barcode " + boxSsccNumber + " is not associated with this shipment",
                        HttpStatus.BAD_REQUEST));

        if (match.getStatus() != ShipmentItemStatus.LOADED) {
            match.setStatus(ShipmentItemStatus.LOADED);
            shipmentItemRepository.save(match);
        }

        boolean allLoaded = shipment.getItems().stream()
                .allMatch(item -> item.getStatus() == ShipmentItemStatus.LOADED);

        if (allLoaded && shipment.getStatus() != ShipmentStatus.LOADED) {
            shipment.setStatus(ShipmentStatus.LOADED);
            shipmentRepository.save(shipment);
            log.info("All items loaded. Shipment status updated to LOADED for: {}", shipmentId);
        }

        return new VerifyLoadResponse(shipmentId, boxSsccNumber, ShipmentItemStatus.LOADED.name(), allLoaded);
    }

    @Transactional
    public Shipment dispatch(Long shipmentId) {
        log.info("Dispatching shipment: {}", shipmentId);

        Shipment shipment = shipmentRepository.findWithDetailsById(shipmentId)
                .orElseThrow(() -> new BusinessException("Shipment not found: " + shipmentId, HttpStatus.NOT_FOUND));

        coreServiceClient.enforceWorkflowStep("OUTBOUND", "SHIPPING", shipmentId, "SHIPMENT");

        if (shipment.getStatus() == ShipmentStatus.DISPATCHED) {
            throw new BusinessException("Shipment is already dispatched!", HttpStatus.BAD_REQUEST);
        }

        boolean hasUnloadedItems = shipment.getItems().stream()
                .anyMatch(item -> item.getStatus() == ShipmentItemStatus.STAGED);

        if (hasUnloadedItems) {
            throw new BusinessException(
                    "Sevkiyattaki tum koliler yuklenene kadar arac cikisina izin verilmez!",
                    HttpStatus.BAD_REQUEST);
        }

        if (shipment.getWarehouseLocationId() == null) {
            throw new BusinessException(
                    "Warehouse location is required on shipment before dispatch", HttpStatus.BAD_REQUEST);
        }

        shipment.setStatus(ShipmentStatus.DISPATCHED);
        shipment.setDispatchedAt(LocalDateTime.now());
        shipmentRepository.save(shipment);

        List<Long> outboundOrderIds = shipment.getItems().stream()
                .map(ShipmentItem::getOutboundOrderId)
                .distinct()
                .toList();

        List<OutboundOrder> orders = outboundOrderRepository.findAllByIdWithItems(outboundOrderIds);
        for (OutboundOrder order : orders) {
            order.setStatus(OutboundOrderStatus.SHIPPED);
        }
        outboundOrderRepository.saveAll(orders);

        InventoryIssueRequest issueRequest = buildInventoryIssueRequest(shipment, orders);
        try {
            inventoryServiceClient.issueStock(issueRequest);
        } catch (Exception e) {
            log.error("Failed to notify inventory service for shipment issue: {}", shipment.getShipmentNumber(), e);
            throw new BusinessException("Inventory service communication error: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        List<String> ssccNumbers = shipment.getItems().stream()
                .map(ShipmentItem::getBoxSsccNumber)
                .toList();

        ShipmentDispatchedEvent event = new ShipmentDispatchedEvent(
                shipment.getId(),
                shipment.getShipmentNumber(),
                shipment.getCompanyId(),
                shipment.getWarehouseLocationId(),
                shipment.getCarrierCode(),
                shipment.getTrackingNumber(),
                shipment.getDispatchedAt(),
                ssccNumbers,
                issueRequest.getLines().stream()
                        .map(line -> new ShipmentDispatchedEvent.IssuedProductLine(
                                line.getProductCode(), line.getQuantity()))
                        .toList()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .aggregateType("Shipment")
                    .aggregateId(shipment.getId())
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxMessageRepository.save(outboxMessage);
        } catch (Exception e) {
            log.error("Failed to write outbox message for shipment: {}", shipment.getShipmentNumber(), e);
            throw new BusinessException("Outbox write error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("Shipment dispatched successfully: {}", shipment.getShipmentNumber());
        return shipment;
    }

    private InventoryIssueRequest buildInventoryIssueRequest(Shipment shipment, List<OutboundOrder> orders) {
        Map<String, BigDecimal> productQuantities = new LinkedHashMap<>();

        for (OutboundOrder order : orders) {
            for (OutboundOrderItem item : order.getItems()) {
                BigDecimal qty = resolveIssueQuantity(item);
                productQuantities.merge(item.getProductCode(), qty, BigDecimal::add);
            }
        }

        List<InventoryIssueRequest.IssueLine> lines = productQuantities.entrySet().stream()
                .map(entry -> InventoryIssueRequest.IssueLine.builder()
                        .productCode(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .toList();

        return InventoryIssueRequest.builder()
                .shipmentId(shipment.getId())
                .shipmentNumber(shipment.getShipmentNumber())
                .companyId(shipment.getCompanyId())
                .warehouseLocationId(shipment.getWarehouseLocationId())
                .lines(lines)
                .build();
    }

    private BigDecimal resolveIssueQuantity(OutboundOrderItem item) {
        if (item.getPickedQuantity() != null && item.getPickedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return item.getPickedQuantity();
        }
        if (item.getAllocatedQuantity() != null && item.getAllocatedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return item.getAllocatedQuantity();
        }
        return item.getQuantity();
    }
}
