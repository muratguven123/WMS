package com.wms.outbound.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.dto.CloseBoxResponse;
import com.wms.outbound.dto.OrderPackedEvent;
import com.wms.outbound.dto.PackingVerifyRequest;
import com.wms.outbound.dto.PackingVerifyResponse;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderItem;
import com.wms.outbound.entity.OutboxMessage;
import com.wms.outbound.entity.PickingItem;
import com.wms.outbound.entity.PickingList;
import com.wms.outbound.entity.enums.OutboundOrderStatus;
import com.wms.outbound.entity.enums.OutboxStatus;
import com.wms.outbound.entity.enums.PickingItemStatus;
import com.wms.outbound.entity.enums.PickingListStatus;
import com.wms.outbound.exception.BusinessException;
import com.wms.outbound.repository.OutboundOrderItemRepository;
import com.wms.outbound.repository.OutboundOrderRepository;
import com.wms.outbound.repository.OutboxMessageRepository;
import com.wms.outbound.repository.PickingItemRepository;
import com.wms.outbound.repository.PickingListRepository;
import com.wms.outbound.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PackingService {

    private final PickingListRepository pickingListRepository;
    private final PickingItemRepository pickingItemRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    private final Random random = new Random();

    /**
     * Verifies scanned items at the packing station.
     */
    @Transactional
    public PackingVerifyResponse verifyPackingItem(PackingVerifyRequest request) {
        log.info("Verifying product scan: {} in picking list: {}", request.productBarcode(), request.pickingListId());

        PickingList list = requireTenantPickingList(request.pickingListId());

        if (list.getStatus() == PickingListStatus.COMPLETED) {
            throw new BusinessException("Picking list is already closed/completed!", HttpStatus.BAD_REQUEST);
        }

        if (list.getStatus() == PickingListStatus.PENDING) {
            list.setStatus(PickingListStatus.IN_PROGRESS);
            pickingListRepository.save(list);
        }

        // Find the first picking item for this product code that is not yet fully verified
        PickingItem match = list.getItems().stream()
                .filter(pi -> pi.getOutboundOrderItem().getProductCode().equalsIgnoreCase(request.productBarcode()))
                .filter(pi -> pi.getPickedQuantity().compareTo(pi.getQuantityToPick()) < 0)
                .findFirst()
                .orElse(null);

        if (match == null) {
            boolean productExistsInList = list.getItems().stream()
                    .anyMatch(pi -> pi.getOutboundOrderItem().getProductCode().equalsIgnoreCase(request.productBarcode()));

            if (productExistsInList) {
                // If it exists but we couldn't find an under-picked one, then it's fully verified (over-scanning)
                throw new BusinessException("Fazla okutma! " + request.productBarcode() + " zaten tamamen doğrulandı.", HttpStatus.BAD_REQUEST);
            } else {
                throw new BusinessException("Okutulan ürün bu toplama listesinde bulunmuyor: " + request.productBarcode(), HttpStatus.BAD_REQUEST);
            }
        }

        BigDecimal totalScanned = match.getPickedQuantity().add(request.scannedQuantity());
        if (totalScanned.compareTo(match.getQuantityToPick()) > 0) {
            throw new BusinessException("Fazla okutma! Girilen miktar hedeflenen toplama miktarını aşıyor.", HttpStatus.BAD_REQUEST);
        }

        // Update picked quantity on the item
        match.setPickedQuantity(totalScanned);
        if (totalScanned.compareTo(match.getQuantityToPick()) == 0) {
            match.setStatus(PickingItemStatus.PICKED);
        }
        pickingItemRepository.save(match);

        // Warning message if still under-picked
        String warningMessage = null;
        if (totalScanned.compareTo(match.getQuantityToPick()) < 0) {
            BigDecimal remaining = match.getQuantityToPick().subtract(totalScanned);
            warningMessage = "Eksik okutma! " + match.getOutboundOrderItem().getProductCode() + " için eksik kalan miktar: " + remaining;
            log.warn(warningMessage);
        }

        return new PackingVerifyResponse(
                match.getId(),
                match.getOutboundOrderItem().getProductCode(),
                match.getQuantityToPick(),
                totalScanned,
                match.getStatus().name(),
                warningMessage
        );
    }

    /**
     * Closes the box for a picking list, generates SSCC, updates statuses, and writes transactional outbox.
     */
    @Transactional
    public CloseBoxResponse closeBox(Long pickingListId) {
        log.info("Closing box for picking list: {}", pickingListId);

        PickingList pickingList = requireTenantPickingList(pickingListId);

        if (pickingList.getStatus() == PickingListStatus.COMPLETED) {
            throw new BusinessException("Picking list/box is already closed!", HttpStatus.BAD_REQUEST);
        }

        // Check if there are any under-picked items (eksik okutma / incomplete verification)
        boolean hasShortages = pickingList.getItems().stream()
                .anyMatch(item -> item.getPickedQuantity().compareTo(item.getQuantityToPick()) < 0);

        if (hasShortages) {
            // Update items with shortage status
            for (PickingItem item : pickingList.getItems()) {
                if (item.getPickedQuantity().compareTo(item.getQuantityToPick()) < 0) {
                    item.setStatus(PickingItemStatus.SHORTAGE);
                    pickingItemRepository.save(item);
                }
            }
            throw new BusinessException("Koli kapatılamadı: Eksik okutulmuş ürünler bulunmaktadır!", HttpStatus.BAD_REQUEST);
        }

        // Generate standard 18-digit SSCC barcode
        String sscc = generateSscc18();
        List<Long> orderIds = new ArrayList<>();
        Set<OutboundOrder> orders = new HashSet<>();

        // Update picking items and order items
        for (PickingItem item : pickingList.getItems()) {
            OutboundOrderItem orderItem = item.getOutboundOrderItem();
            orderItem.setPickedQuantity(item.getPickedQuantity());
            outboundOrderItemRepository.save(orderItem);

            orders.add(orderItem.getOutboundOrder());
        }

        // Update sales orders to PACKED and write to transactional outbox
        for (OutboundOrder order : orders) {
            order.setStatus(OutboundOrderStatus.PACKED);
            outboundOrderRepository.save(order);
            orderIds.add(order.getId());

            // Build outbox event payload
            List<OrderPackedEvent.PackedItemDto> packedItems = order.getItems().stream()
                    .map(oi -> new OrderPackedEvent.PackedItemDto(oi.getProductCode(), oi.getPickedQuantity()))
                    .toList();

            OrderPackedEvent event = new OrderPackedEvent(
                    order.getId(),
                    order.getOrderNumber(),
                    sscc,
                    LocalDateTime.now(),
                    packedItems
            );

            try {
                String payload = objectMapper.writeValueAsString(event);
                OutboxMessage outboxMessage = OutboxMessage.builder()
                        .aggregateType("OutboundOrder")
                        .aggregateId(order.getId())
                        .payload(payload)
                        .status(OutboxStatus.PENDING)
                        .build();
                outboxMessageRepository.save(outboxMessage);
            } catch (Exception e) {
                log.error("Failed to write outbox message for order: {}", order.getOrderNumber(), e);
                throw new BusinessException("Outbox write error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // Update picking list status to COMPLETED
        pickingList.setStatus(PickingListStatus.COMPLETED);
        pickingListRepository.save(pickingList);

        log.info("Box closed successfully with SSCC: {} for picking list: {}", sscc, pickingListId);
        return new CloseBoxResponse(pickingListId, sscc, orderIds, "PACKED");
    }

    private PickingList requireTenantPickingList(Long pickingListId) {
        PickingList list = pickingListRepository.findWithDetailsById(pickingListId)
                .orElseThrow(() -> new BusinessException(
                        "Picking list not found: " + pickingListId, HttpStatus.NOT_FOUND));
        TenantScopeGuard.assertEntityBelongsToContext(list.getWarehouseLocationId(), list.getCompanyId());
        return list;
    }

    /**
     * Generates a mathematically correct GS1 SSCC-18 serial shipping container code.
     */
    private String generateSscc18() {
        // Extension digit: 3
        // GS1 Company Prefix: 1234567
        // Serial Reference: 9 random digits
        long serialReference = (long) (random.nextDouble() * 1_000_000_000L);
        String base = "31234567" + String.format("%09d", serialReference);

        // Modulo 10 Checksum Calculation
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            int val = Character.getNumericValue(base.charAt(i));
            sum += val * (i % 2 == 0 ? 3 : 1);
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return base + checkDigit;
    }
}
