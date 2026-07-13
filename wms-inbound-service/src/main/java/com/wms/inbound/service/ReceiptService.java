package com.wms.inbound.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.dto.*;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderItem;
import com.wms.inbound.entity.OutboxMessage;
import com.wms.inbound.entity.Receipt;
import com.wms.inbound.entity.ReceiptItem;
import com.wms.inbound.entity.enums.InboundOrderStatus;
import com.wms.inbound.entity.enums.OutboxStatus;
import com.wms.inbound.entity.enums.ReceiptItemQcStatus;
import com.wms.inbound.entity.enums.ReceiptStatus;
import com.wms.inbound.exception.BusinessException;
import com.wms.inbound.repository.InboundOrderRepository;
import com.wms.inbound.repository.OutboxMessageRepository;
import com.wms.inbound.repository.ReceiptRepository;
import com.wms.inbound.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final InboundOrderRepository inboundOrderRepository;
    private final ReceiptRepository receiptRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final PutawayEngineService putawayEngineService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<ReceiptResponse> listReceipts(ReceiptStatus status, Pageable pageable) {
        Long companyId = TenantScopeGuard.requireCompanyId();
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();
        Page<Receipt> page = status != null
                ? receiptRepository.findByInboundOrder_CompanyIdAndInboundOrder_WarehouseLocationIdAndStatus(
                        companyId, warehouseLocationId, status, pageable)
                : receiptRepository.findByInboundOrder_CompanyIdAndInboundOrder_WarehouseLocationId(
                        companyId, warehouseLocationId, pageable);
        return page.map(this::mapToResponse);
    }

    @Transactional
    public ReceiptResponse startReceipt(StartReceiptRequest request) {
        log.info("Starting receipt for inbound order ID: {}", request.inboundOrderId());

        InboundOrder inboundOrder = inboundOrderRepository.findByIdWithItems(request.inboundOrderId())
                .orElseThrow(() -> new BusinessException(
                        "Inbound order not found: " + request.inboundOrderId(), HttpStatus.NOT_FOUND));

        if (inboundOrder.getStatus() == InboundOrderStatus.CANCELLED
                || inboundOrder.getStatus() == InboundOrderStatus.COMPLETED) {
            throw new BusinessException(
                    "Cannot receive items for order in status: " + inboundOrder.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        TenantScopeGuard.assertEntityBelongsToContext(
                inboundOrder.getWarehouseLocationId(), inboundOrder.getCompanyId());

        if (receiptRepository.findByReceiptNumber(request.receiptNumber()).isPresent()) {
            throw new BusinessException(
                    "Receipt number already exists: " + request.receiptNumber(),
                    HttpStatus.CONFLICT);
        }

        inboundOrder.setStatus(InboundOrderStatus.RECEIVING);
        inboundOrderRepository.save(inboundOrder);

        Receipt receipt = Receipt.builder()
                .inboundOrder(inboundOrder)
                .receiptNumber(request.receiptNumber())
                .receivedByUserId(request.receivedByUserId())
                .receivedAt(LocalDateTime.now())
                .status(ReceiptStatus.QC_PENDING)
                .build();

        List<ReceiptItem> receiptItems = request.items().stream()
                .map(itemReq -> {
                    inboundOrder.getItems().stream()
                            .filter(oi -> oi.getProductCode().equals(itemReq.productCode()))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(
                                    "Product code " + itemReq.productCode() + " does not belong to inbound order",
                                    HttpStatus.BAD_REQUEST));

                    return ReceiptItem.builder()
                            .receipt(receipt)
                            .productCode(itemReq.productCode())
                            .quantity(itemReq.quantity())
                            .qcStatus(ReceiptItemQcStatus.PENDING)
                            .build();
                })
                .collect(Collectors.toList());

        receipt.setItems(receiptItems);
        Receipt saved = receiptRepository.save(receipt);

        log.info("Receipt started successfully. ID: {}, Number: {}", saved.getId(), saved.getReceiptNumber());
        return mapToResponse(saved);
    }

    @Transactional
    public ReceiptResponse inputQcResults(Long receiptId, ReceiptQcRequest request) {
        log.info("Inputting QC results for receipt ID: {}", receiptId);

        Receipt receipt = loadReceipt(receiptId);

        if (receipt.getStatus() != ReceiptStatus.QC_PENDING) {
            throw new BusinessException(
                    "QC results can only be entered for receipts in QC_PENDING status",
                    HttpStatus.BAD_REQUEST);
        }

        for (ReceiptItemQcRequest qcReq : request.items()) {
            ReceiptItem match = receipt.getItems().stream()
                    .filter(ri -> ri.getProductCode().equals(qcReq.productCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "Product code " + qcReq.productCode() + " not found in receipt",
                            HttpStatus.BAD_REQUEST));

            match.setQcStatus(qcReq.qcStatus());
            match.setLotNumber(qcReq.lotNumber());
            match.setSerialNumber(qcReq.serialNumber());
        }

        Receipt saved = receiptRepository.save(receipt);
        log.info("QC results recorded for receipt ID: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public ReceiptResponse approveReceipt(Long receiptId) {
        log.info("Approving receipt ID: {}", receiptId);

        Receipt receipt = loadReceipt(receiptId);

        if (receipt.getStatus() != ReceiptStatus.QC_PENDING) {
            throw new BusinessException(
                    "Receipt status is " + receipt.getStatus() + ". Only QC_PENDING receipts can be approved.",
                    HttpStatus.BAD_REQUEST);
        }

        boolean qcIncomplete = receipt.getItems().stream()
                .anyMatch(item -> item.getQcStatus() == ReceiptItemQcStatus.PENDING);
        if (qcIncomplete) {
            throw new BusinessException(
                    "All receipt items must have QC results before approval",
                    HttpStatus.BAD_REQUEST);
        }

        boolean hasPassedItems = receipt.getItems().stream()
                .anyMatch(item -> item.getQcStatus() == ReceiptItemQcStatus.PASSED);
        if (!hasPassedItems) {
            throw new BusinessException(
                    "At least one item must pass QC before approval",
                    HttpStatus.BAD_REQUEST);
        }

        receipt.setStatus(ReceiptStatus.APPROVED);

        InboundOrder order = receipt.getInboundOrder();
        Long warehouseLocationId = order.getWarehouseLocationId();

        for (ReceiptItem item : receipt.getItems()) {
            if (item.getQcStatus() == ReceiptItemQcStatus.PASSED) {
                InboundOrderItem orderItem = order.getItems().stream()
                        .filter(oi -> oi.getProductCode().equals(item.getProductCode()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "Product " + item.getProductCode() + " not found in purchase order lines",
                                HttpStatus.BAD_REQUEST));

                BigDecimal currentReceived = orderItem.getReceivedQuantity() != null
                        ? orderItem.getReceivedQuantity() : BigDecimal.ZERO;
                orderItem.setReceivedQuantity(currentReceived.add(item.getQuantity()));
            }
        }

        boolean isAllCompleted = order.getItems().stream()
                .allMatch(oi -> oi.getReceivedQuantity().compareTo(oi.getQuantity()) >= 0);
        order.setStatus(isAllCompleted ? InboundOrderStatus.COMPLETED : InboundOrderStatus.RECEIVING);
        inboundOrderRepository.save(order);

        Receipt savedReceipt = receiptRepository.save(receipt);
        publishReceiptApprovedOutbox(savedReceipt, order, warehouseLocationId);

        List<ReceiptItemEventDto> eventItems = savedReceipt.getItems().stream()
                .filter(item -> item.getQcStatus() == ReceiptItemQcStatus.PASSED)
                .map(item -> {
                    Long recommendedLocation = warehouseLocationId != null
                            ? putawayEngineService.findPutawayLocation(item, warehouseLocationId).orElse(null)
                            : null;
                    return new ReceiptItemEventDto(
                            item.getProductCode(),
                            item.getQuantity(),
                            item.getLotNumber() != null ? item.getLotNumber() : "",
                            item.getSerialNumber() != null ? item.getSerialNumber() : "",
                            recommendedLocation
                    );
                })
                .toList();

        ReceiptApprovedEvent approvedEvent = new ReceiptApprovedEvent(
                savedReceipt.getId(),
                order.getId(),
                order.getCompanyId(),
                warehouseLocationId,
                Instant.now(),
                eventItems
        );

        eventPublisher.publishEvent(approvedEvent);

        return mapToResponse(savedReceipt);
    }

    private void publishReceiptApprovedOutbox(
            Receipt savedReceipt, InboundOrder order, Long warehouseLocationId) {

        try {
            List<Map<String, Object>> approvedItems = savedReceipt.getItems().stream()
                    .filter(item -> item.getQcStatus() == ReceiptItemQcStatus.PASSED)
                    .map(item -> {
                        Map<String, Object> itemPayload = new HashMap<>();
                        itemPayload.put("productCode", item.getProductCode());
                        itemPayload.put("quantity", item.getQuantity());
                        itemPayload.put("lotNumber", item.getLotNumber() != null ? item.getLotNumber() : "");
                        itemPayload.put("serialNumber", item.getSerialNumber() != null ? item.getSerialNumber() : "");

                        if (warehouseLocationId != null) {
                            putawayEngineService.findPutawayLocation(item, warehouseLocationId)
                                    .ifPresent(locId -> itemPayload.put("recommendedStorageLocationId", locId));
                        }
                        return itemPayload;
                    })
                    .toList();

            Map<String, Object> outboxPayload = new HashMap<>();
            outboxPayload.put("receiptId", savedReceipt.getId());
            outboxPayload.put("receiptNumber", savedReceipt.getReceiptNumber());
            outboxPayload.put("inboundOrderNumber", order.getOrderNumber());
            outboxPayload.put("companyId", order.getCompanyId());
            outboxPayload.put("warehouseLocationId", warehouseLocationId);
            outboxPayload.put("approvedItems", approvedItems);

            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .aggregateType("Receipt")
                    .aggregateId(savedReceipt.getId())
                    .payload(objectMapper.writeValueAsString(outboxPayload))
                    .status(OutboxStatus.PENDING)
                    .build();

            outboxMessageRepository.save(outboxMessage);
            log.info("Transactional outbox message recorded for receipt ID: {}", savedReceipt.getId());

        } catch (Exception e) {
            log.error("Failed to generate transactional outbox message for receipt ID: {}",
                    savedReceipt.getId(), e);
            throw new BusinessException(
                    "Failed to save outbox message: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Receipt loadReceipt(Long receiptId) {
        Receipt receipt = receiptRepository.findWithDetailsById(receiptId)
                .orElseThrow(() -> new BusinessException(
                        "Receipt not found: " + receiptId, HttpStatus.NOT_FOUND));
        InboundOrder order = receipt.getInboundOrder();
        TenantScopeGuard.assertEntityBelongsToContext(order.getWarehouseLocationId(), order.getCompanyId());
        return receipt;
    }

    private ReceiptResponse mapToResponse(Receipt receipt) {
        List<ReceiptItemResponse> itemResponses = receipt.getItems().stream()
                .map(item -> new ReceiptItemResponse(
                        item.getId(),
                        item.getProductCode(),
                        item.getQuantity(),
                        item.getLotNumber(),
                        item.getSerialNumber(),
                        item.getQcStatus()))
                .toList();

        return new ReceiptResponse(
                receipt.getId(),
                receipt.getInboundOrder().getId(),
                receipt.getReceiptNumber(),
                receipt.getReceivedByUserId(),
                receipt.getReceivedAt(),
                receipt.getStatus(),
                itemResponses);
    }
}
