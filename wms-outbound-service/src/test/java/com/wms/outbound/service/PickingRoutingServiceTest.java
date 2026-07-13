package com.wms.outbound.service;

import com.wms.outbound.dto.AllocateStockRequest;
import com.wms.outbound.dto.AllocatedStockDto;
import com.wms.outbound.dto.PickingListResponse;
import com.wms.outbound.dto.StorageLocationResponse;
import com.wms.outbound.entity.enums.StorageLocationStatus;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderItem;
import com.wms.outbound.entity.PickingList;
import com.wms.outbound.entity.enums.OutboundOrderStatus;
import com.wms.outbound.entity.enums.PickingListStatus;
import com.wms.outbound.exception.BusinessException;
import com.wms.outbound.integration.CoreServiceClient;
import com.wms.outbound.integration.InventoryServiceClient;
import com.wms.outbound.repository.OutboundOrderRepository;
import com.wms.outbound.repository.PickingListRepository;
import com.wms.outbound.security.TenantContext;
import com.wms.outbound.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickingRoutingServiceTest {

    @Mock
    private OutboundOrderRepository outboundOrderRepository;

    @Mock
    private PickingListRepository pickingListRepository;

    @Mock
    private CoreServiceClient coreServiceClient;

    @Mock
    private InventoryServiceClient inventoryServiceClient;

    @InjectMocks
    private PickingRoutingService pickingRoutingService;

    private Long warehouseLocationId;
    private OutboundOrder order;
    private OutboundOrderItem item1;
    private OutboundOrderItem item2;

    @BeforeEach
    void setUp() {
        warehouseLocationId = 1L;
        TenantContextHolder.setContext(new TenantContext(1L, 1L, warehouseLocationId));

        order = OutboundOrder.builder()
                .id(1L)
                .orderNumber("ORD-TEST")
                .companyId(1L)
                .warehouseLocationId(warehouseLocationId)
                .customerId(1L)
                .orderDate(LocalDateTime.now())
                .status(OutboundOrderStatus.PENDING)
                .shippingAddressId(1L)
                .build();

        item1 = OutboundOrderItem.builder()
                .id(1L)
                .outboundOrder(order)
                .productCode("PROD-1")
                .quantity(new BigDecimal("10.0000"))
                .allocatedQuantity(BigDecimal.ZERO)
                .pickedQuantity(BigDecimal.ZERO)
                .build();

        item2 = OutboundOrderItem.builder()
                .id(1L)
                .outboundOrder(order)
                .productCode("PROD-2")
                .quantity(new BigDecimal("5.0000"))
                .allocatedQuantity(BigDecimal.ZERO)
                .pickedQuantity(BigDecimal.ZERO)
                .build();

        order.setItems(List.of(item1, item2));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createPickingList_rejectsOrderFromAnotherWarehouse() {
        order.setWarehouseLocationId(2L);
        when(outboundOrderRepository.findAllByIdWithItems(List.of(order.getId()))).thenReturn(List.of(order));

        assertThatThrownBy(() -> pickingRoutingService.createPickingList(
                List.of(order.getId()), warehouseLocationId, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createPickingList_shouldAllocateStockAndSortItemsUsingSShapeRoute() {
        // Arrange
        Long orderId = order.getId();
        when(outboundOrderRepository.findAllByIdWithItems(List.of(orderId))).thenReturn(List.of(order));

        Long locIdA1 = 1L;
        Long locIdA2 = 4L;
        Long locIdB1 = 2L;
        Long locIdB2 = 3L;
        Long locIdA2High = 5L;

        // Mock allocations from inventory service
        // For item1 ("PROD-1", qty 10): allocates 3 pieces from A-01-01, 4 from B-02-01, 3 from B-01-01
        when(inventoryServiceClient.allocateStock(argThat(req -> req != null && "PROD-1".equals(req.productCode()))))
                .thenReturn(List.of(
                        new AllocatedStockDto(locIdA1, "LOT-1", new BigDecimal("3.0000")),
                        new AllocatedStockDto(locIdB2, "LOT-1", new BigDecimal("4.0000")),
                        new AllocatedStockDto(locIdB1, "LOT-1", new BigDecimal("3.0000"))
                ));

        // For item2 ("PROD-2", qty 5): allocates 3 from A-02-01, 2 from A-02-02
        when(inventoryServiceClient.allocateStock(argThat(req -> req != null && "PROD-2".equals(req.productCode()))))
                .thenReturn(List.of(
                        new AllocatedStockDto(locIdA2, "LOT-2", new BigDecimal("3.0000")),
                        new AllocatedStockDto(locIdA2High, "LOT-2", new BigDecimal("2.0000"))
                ));

        // Mock location details from core service
        // Aisle A is ODD (A=1): Bay descending (02 comes before 01)
        // Aisle B is EVEN (B=2): Bay ascending (01 comes before 02)
        when(coreServiceClient.getStorageLocation(locIdA1)).thenReturn(
                location(locIdA1, "A-01-01", "A", "01", "01"));
        when(coreServiceClient.getStorageLocation(locIdB2)).thenReturn(
                location(locIdB2, "B-02-01", "B", "02", "01"));
        when(coreServiceClient.getStorageLocation(locIdB1)).thenReturn(
                location(locIdB1, "B-01-01", "B", "01", "01"));
        when(coreServiceClient.getStorageLocation(locIdA2)).thenReturn(
                location(locIdA2, "A-02-01", "A", "02", "01"));
        when(coreServiceClient.getStorageLocation(locIdA2High)).thenReturn(
                location(locIdA2High, "A-02-02", "A", "02", "02"));

        // Captures saved picking list
        when(pickingListRepository.save(any(PickingList.class))).thenAnswer(invocation -> {
            PickingList pl = invocation.getArgument(0);
            pl.setId(1L);
            return pl;
        });

        Long createdByUserId = 1L;
        PickingListResponse createdList = pickingRoutingService.createPickingList(
                List.of(orderId), warehouseLocationId, createdByUserId);

        assertThat(createdList).isNotNull();
        assertThat(createdList.status()).isEqualTo(PickingListStatus.PENDING);
        assertThat(createdList.warehouseLocationId()).isEqualTo(warehouseLocationId);
        assertThat(createdList.createdByUserId()).isEqualTo(createdByUserId);

        // Verify allocations on order items
        assertThat(item1.getAllocatedQuantity()).isEqualByComparingTo("10.0000");
        assertThat(item2.getAllocatedQuantity()).isEqualByComparingTo("5.0000");
        assertThat(order.getStatus()).isEqualTo(OutboundOrderStatus.PICKING);

        // Verify S-Shape sorting order of PickingItems:
        // Expected order:
        // 1. A-02-01 (Aisle A - ODD, Bay 02 - largest, Shelf 01)
        // 2. A-02-02 (Aisle A - ODD, Bay 02 - largest, Shelf 02)
        // 3. A-01-01 (Aisle A - ODD, Bay 01 - smallest, Shelf 01)
        // 4. B-01-01 (Aisle B - EVEN, Bay 01 - smallest, Shelf 01)
        // 5. B-02-01 (Aisle B - EVEN, Bay 02 - largest, Shelf 01)
        List<PickingListResponse.PickingItemResponse> items = createdList.items();
        assertThat(items).hasSize(5);
        assertThat(items.get(0).addressCode()).isEqualTo("A-02-01");
        assertThat(items.get(1).addressCode()).isEqualTo("A-02-02");
        assertThat(items.get(2).addressCode()).isEqualTo("A-01-01");
        assertThat(items.get(3).addressCode()).isEqualTo("B-01-01");
        assertThat(items.get(4).addressCode()).isEqualTo("B-02-01");
    }

    private StorageLocationResponse location(Long id, String addressCode, String aisle, String bay, String shelf) {
        return new StorageLocationResponse(
                id, null, null, null, null, addressCode, aisle, bay, shelf, "01",
                null, null, null, null, null, StorageLocationStatus.ACTIVE, true);
    }
}
