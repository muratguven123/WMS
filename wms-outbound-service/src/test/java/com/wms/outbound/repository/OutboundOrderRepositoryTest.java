package com.wms.outbound.repository;

import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderItem;
import com.wms.outbound.entity.PickingList;
import com.wms.outbound.entity.PickingItem;
import com.wms.outbound.entity.enums.OutboundOrderStatus;
import com.wms.outbound.entity.enums.PickingItemStatus;
import com.wms.outbound.entity.enums.PickingListStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.wms.outbound.config.OutboundTestMessagingConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Import(OutboundTestMessagingConfig.class)
@Transactional
class OutboundOrderRepositoryTest {

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private OutboundOrderItemRepository outboundOrderItemRepository;

    @Autowired
    private PickingListRepository pickingListRepository;

    @Autowired
    private PickingItemRepository pickingItemRepository;

    @Test
    void shouldSaveAndRetrieveOutboundOrderWithItems() {
        // Arrange
        Long companyId = 1L;
        Long customerId = 1L;
        Long shippingAddressId = 1L;

        OutboundOrder order = OutboundOrder.builder()
                .orderNumber("ORD-999")
                .companyId(companyId)
                .warehouseLocationId(1L)
                .customerId(customerId)
                .orderDate(LocalDateTime.now())
                .status(OutboundOrderStatus.PENDING)
                .shippingAddressId(shippingAddressId)
                .build();

        OutboundOrderItem item1 = OutboundOrderItem.builder()
                .outboundOrder(order)
                .productCode("PROD-A")
                .quantity(new BigDecimal("10.0000"))
                .allocatedQuantity(new BigDecimal("5.0000"))
                .pickedQuantity(BigDecimal.ZERO)
                .build();

        OutboundOrderItem item2 = OutboundOrderItem.builder()
                .outboundOrder(order)
                .productCode("PROD-B")
                .quantity(new BigDecimal("20.0000"))
                .allocatedQuantity(BigDecimal.ZERO)
                .pickedQuantity(BigDecimal.ZERO)
                .build();

        order.setItems(List.of(item1, item2));

        // Act
        OutboundOrder savedOrder = outboundOrderRepository.save(order);
        outboundOrderRepository.flush();

        // Assert
        assertThat(savedOrder.getId()).isNotNull();
        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getItems()).hasSize(2);

        Optional<OutboundOrder> retrievedOrderOpt = outboundOrderRepository.findById(savedOrder.getId());
        assertThat(retrievedOrderOpt).isPresent();
        
        OutboundOrder retrievedOrder = retrievedOrderOpt.get();
        assertThat(retrievedOrder.getOrderNumber()).isEqualTo("ORD-999");
        assertThat(retrievedOrder.getStatus()).isEqualTo(OutboundOrderStatus.PENDING);
        assertThat(retrievedOrder.getItems().get(0).getProductCode()).isEqualTo("PROD-A");
        assertThat(retrievedOrder.getItems().get(0).getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    void shouldEnforceUniqueOrderNumber() {
        // Arrange
        Long companyId = 1L;
        Long customerId = 1L;
        Long shippingAddressId = 1L;

        OutboundOrder order1 = OutboundOrder.builder()
                .orderNumber("ORD-DUP")
                .companyId(companyId)
                .warehouseLocationId(1L)
                .customerId(customerId)
                .orderDate(LocalDateTime.now())
                .status(OutboundOrderStatus.PENDING)
                .shippingAddressId(shippingAddressId)
                .build();

        OutboundOrder order2 = OutboundOrder.builder()
                .orderNumber("ORD-DUP")
                .companyId(companyId)
                .warehouseLocationId(1L)
                .customerId(customerId)
                .orderDate(LocalDateTime.now())
                .status(OutboundOrderStatus.PENDING)
                .shippingAddressId(shippingAddressId)
                .build();

        outboundOrderRepository.save(order1);
        outboundOrderRepository.flush();

        // Act & Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            outboundOrderRepository.save(order2);
            outboundOrderRepository.flush();
        });
    }

    @Test
    void shouldSaveAndRetrievePickingListWithItems() {
        // Arrange
        Long companyId = 1L;
        Long customerId = 1L;
        Long shippingAddressId = 1L;
        Long warehouseLocationId = 1L;
        Long createdByUserId = 1L;
        Long sourceLocationId = 1L;

        // Create Order and Item first to satisfy foreign key
        OutboundOrder order = OutboundOrder.builder()
                .orderNumber("ORD-888")
                .companyId(companyId)
                .warehouseLocationId(1L)
                .customerId(customerId)
                .orderDate(LocalDateTime.now())
                .status(OutboundOrderStatus.PENDING)
                .shippingAddressId(shippingAddressId)
                .build();

        OutboundOrderItem item = OutboundOrderItem.builder()
                .outboundOrder(order)
                .productCode("PROD-C")
                .quantity(new BigDecimal("15.0000"))
                .build();
        order.setItems(List.of(item));

        outboundOrderRepository.save(order);

        // Create Picking List
        PickingList pickingList = PickingList.builder()
                .warehouseLocationId(warehouseLocationId)
                .createdByUserId(createdByUserId)
                .status(PickingListStatus.PENDING)
                .build();

        PickingItem pickingItem = PickingItem.builder()
                .pickingList(pickingList)
                .outboundOrderItem(item)
                .sourceLocationId(sourceLocationId)
                .addressCode("A-12-03-01")
                .quantityToPick(new BigDecimal("5.0000"))
                .pickedQuantity(BigDecimal.ZERO)
                .status(PickingItemStatus.PENDING)
                .build();

        pickingList.setItems(List.of(pickingItem));

        // Act
        PickingList savedPickingList = pickingListRepository.save(pickingList);
        pickingListRepository.flush();

        // Assert
        assertThat(savedPickingList.getId()).isNotNull();
        assertThat(savedPickingList.getCreatedAt()).isNotNull();
        assertThat(savedPickingList.getItems()).hasSize(1);

        Optional<PickingList> retrievedListOpt = pickingListRepository.findById(savedPickingList.getId());
        assertThat(retrievedListOpt).isPresent();

        PickingList retrievedList = retrievedListOpt.get();
        assertThat(retrievedList.getStatus()).isEqualTo(PickingListStatus.PENDING);
        assertThat(retrievedList.getItems().get(0).getAddressCode()).isEqualTo("A-12-03-01");
        assertThat(retrievedList.getItems().get(0).getQuantityToPick()).isEqualByComparingTo("5.0000");
    }
}
