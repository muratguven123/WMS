package com.wms.outbound.service;

import com.wms.outbound.dto.AllocateStockRequest;
import com.wms.outbound.dto.AllocatedStockDto;
import com.wms.outbound.dto.PickingListResponse;
import com.wms.outbound.dto.StorageLocationResponse;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderItem;
import com.wms.outbound.entity.PickingItem;
import com.wms.outbound.entity.PickingList;
import com.wms.outbound.entity.enums.OutboundOrderStatus;
import com.wms.outbound.entity.enums.PickingItemStatus;
import com.wms.outbound.entity.enums.PickingListStatus;
import com.wms.outbound.exception.BusinessException;
import com.wms.outbound.integration.CoreServiceClient;
import com.wms.outbound.integration.InventoryServiceClient;
import com.wms.outbound.repository.OutboundOrderRepository;
import com.wms.outbound.repository.PickingListRepository;
import com.wms.outbound.security.TenantScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickingRoutingService {

    private final OutboundOrderRepository outboundOrderRepository;
    private final PickingListRepository pickingListRepository;
    private final CoreServiceClient coreServiceClient;
    private final InventoryServiceClient inventoryServiceClient;

    @Transactional(readOnly = true)
    public Page<PickingListResponse> listPickingLists(Pageable pageable) {
        Long companyId = TenantScopeGuard.requireCompanyId();
        Long warehouseLocationId = TenantScopeGuard.requireWarehouseLocationId();
        return pickingListRepository
                .findByCompanyIdAndWarehouseLocationId(companyId, warehouseLocationId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public PickingListResponse createPickingList(
            List<Long> outboundOrderIds,
            Long warehouseLocationId,
            Long createdByUserId) {

        Long activeWarehouseId = TenantScopeGuard.requireWarehouseLocationId();
        Long activeCompanyId = TenantScopeGuard.requireCompanyId();
        TenantScopeGuard.assertMatchesContext(warehouseLocationId);
        warehouseLocationId = activeWarehouseId;

        coreServiceClient.enforceWorkflowStep("OUTBOUND", "PICKING", null, "PICKING_LIST");

        log.info("Starting Picking List creation for orders: {} in warehouse: {}",
                outboundOrderIds, warehouseLocationId);

        if (outboundOrderIds == null || outboundOrderIds.isEmpty()) {
            throw new BusinessException("At least one outbound order id is required", HttpStatus.BAD_REQUEST);
        }

        List<OutboundOrder> orders = outboundOrderRepository.findAllByIdWithItems(outboundOrderIds);
        if (orders.size() != outboundOrderIds.size()) {
            throw new BusinessException("One or more outbound orders were not found", HttpStatus.NOT_FOUND);
        }

        List<PickingAllocation> allAllocations = new ArrayList<>();

        for (OutboundOrder order : orders) {
            TenantScopeGuard.assertEntityBelongsToContext(order.getWarehouseLocationId(), order.getCompanyId());
            if (!activeCompanyId.equals(order.getCompanyId())) {
                throw new BusinessException("Outbound order belongs to another company", HttpStatus.FORBIDDEN);
            }
            if (!activeWarehouseId.equals(order.getWarehouseLocationId())) {
                throw new BusinessException("Outbound order belongs to another warehouse", HttpStatus.FORBIDDEN);
            }

            if (order.getStatus() == OutboundOrderStatus.PACKED
                    || order.getStatus() == OutboundOrderStatus.SHIPPED) {
                throw new BusinessException(
                        "Order " + order.getOrderNumber() + " cannot be picked in status " + order.getStatus(),
                        HttpStatus.BAD_REQUEST);
            }

            for (OutboundOrderItem item : order.getItems()) {
                BigDecimal remainingToAllocate = item.getQuantity().subtract(item.getAllocatedQuantity());
                if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                List<AllocatedStockDto> allocations = inventoryServiceClient.allocateStock(
                        new AllocateStockRequest(item.getProductCode(), remainingToAllocate, "FIFO", warehouseLocationId));

                if (allocations.isEmpty()) {
                    throw new BusinessException(
                            "No stock allocated for product " + item.getProductCode() + " on order "
                                    + order.getOrderNumber(),
                            HttpStatus.BAD_REQUEST);
                }

                for (AllocatedStockDto allocDto : allocations) {
                    if (allocDto.allocatedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    StorageLocationResponse loc = coreServiceClient.getStorageLocation(allocDto.storageLocationId());

                    allAllocations.add(new PickingAllocation(
                            item,
                            allocDto.storageLocationId(),
                            loc.addressCode(),
                            allocDto.allocatedQuantity(),
                            loc.aisle(),
                            loc.bay(),
                            loc.shelf()));

                    item.setAllocatedQuantity(item.getAllocatedQuantity().add(allocDto.allocatedQuantity()));
                }
            }
        }

        if (allAllocations.isEmpty()) {
            throw new BusinessException(
                    "No stock could be allocated for the selected outbound orders",
                    HttpStatus.BAD_REQUEST);
        }

        allAllocations.sort(getSShapeComparator());

        PickingList pickingList = PickingList.builder()
                .warehouseLocationId(warehouseLocationId)
                .companyId(orders.get(0).getCompanyId())
                .createdByUserId(createdByUserId)
                .status(PickingListStatus.PENDING)
                .build();

        List<PickingItem> pickingItems = new ArrayList<>();
        for (PickingAllocation alloc : allAllocations) {
            pickingItems.add(PickingItem.builder()
                    .pickingList(pickingList)
                    .outboundOrderItem(alloc.orderItem())
                    .sourceLocationId(alloc.sourceLocationId())
                    .addressCode(alloc.addressCode())
                    .quantityToPick(alloc.quantityToPick())
                    .pickedQuantity(BigDecimal.ZERO)
                    .status(PickingItemStatus.PENDING)
                    .build());
        }
        pickingList.setItems(pickingItems);

        for (OutboundOrder order : orders) {
            boolean allAllocated = order.getItems().stream()
                    .allMatch(item -> item.getAllocatedQuantity().compareTo(item.getQuantity()) >= 0);
            order.setStatus(allAllocated ? OutboundOrderStatus.PICKING : OutboundOrderStatus.ALLOCATED);
            outboundOrderRepository.save(order);
        }

        PickingList saved = pickingListRepository.save(pickingList);
        log.info("Picking list {} created with {} items", saved.getId(), saved.getItems().size());
        return mapToResponse(saved);
    }

    public PickingListResponse mapToResponse(PickingList list) {
        List<PickingListResponse.PickingItemResponse> items = list.getItems().stream()
                .map(item -> new PickingListResponse.PickingItemResponse(
                        item.getId(),
                        item.getOutboundOrderItem().getId(),
                        item.getOutboundOrderItem().getProductCode(),
                        item.getSourceLocationId(),
                        item.getAddressCode(),
                        item.getQuantityToPick(),
                        item.getPickedQuantity(),
                        item.getStatus()))
                .toList();

        return new PickingListResponse(
                list.getId(),
                list.getWarehouseLocationId(),
                list.getCompanyId(),
                list.getCreatedByUserId(),
                list.getAssignedUserId(),
                list.getAssignedAt(),
                list.getStatus(),
                list.getCreatedAt(),
                items);
    }

    private Comparator<PickingAllocation> getSShapeComparator() {
        return (a, b) -> {
            int aisleCompare = compareStrings(a.aisle(), b.aisle());
            if (aisleCompare != 0) {
                return aisleCompare;
            }

            boolean evenAisle = isAisleEven(a.aisle());
            int bayA = parseNumeric(a.bay());
            int bayB = parseNumeric(b.bay());
            int bayCompare = evenAisle ? Integer.compare(bayA, bayB) : Integer.compare(bayB, bayA);
            if (bayCompare != 0) {
                return bayCompare;
            }

            return Integer.compare(parseNumeric(a.shelf()), parseNumeric(b.shelf()));
        };
    }

    private int compareStrings(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;
        return s1.compareTo(s2);
    }

    private boolean isAisleEven(String aisle) {
        if (aisle == null || aisle.trim().isEmpty()) {
            return false;
        }
        String digits = aisle.replaceAll("\\D+", "");
        if (!digits.isEmpty()) {
            try {
                return Integer.parseInt(digits) % 2 == 0;
            } catch (NumberFormatException ignored) {
            }
        }
        char firstChar = aisle.trim().toUpperCase().charAt(0);
        if (firstChar >= 'A' && firstChar <= 'Z') {
            return (firstChar - 'A' + 1) % 2 == 0;
        }
        return false;
    }

    private int parseNumeric(String s) {
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }
        String digits = s.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record PickingAllocation(
            OutboundOrderItem orderItem,
            Long sourceLocationId,
            String addressCode,
            BigDecimal quantityToPick,
            String aisle,
            String bay,
            String shelf) {
    }
}
