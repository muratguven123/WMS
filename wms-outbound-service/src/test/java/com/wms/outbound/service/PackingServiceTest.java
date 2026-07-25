package com.wms.outbound.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.dto.CloseBoxResponse;
import com.wms.outbound.dto.OrderPackedEvent;
import com.wms.outbound.dto.PackingVerifyRequest;
import com.wms.outbound.dto.PackingVerifyResponse;
import com.wms.outbound.entity.*;
import com.wms.outbound.entity.enums.OutboundOrderStatus;
import com.wms.outbound.entity.enums.OutboxStatus;
import com.wms.outbound.entity.enums.PickingItemStatus;
import com.wms.outbound.entity.enums.PickingListStatus;
import com.wms.outbound.exception.BusinessException;
import com.wms.outbound.integration.CoreServiceClient;
import com.wms.outbound.repository.*;
import com.wms.outbound.security.TenantContext;
import com.wms.outbound.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.wms.outbound.config.OutboundTestMessagingConfig;
import com.wms.outbound.dto.WorkflowEnforceResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(OutboundTestMessagingConfig.class)
@Transactional
class PackingServiceTest extends com.wms.outbound.OutboundPostgresTestBase {

    @Autowired
    private PackingService packingService;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private PickingListRepository pickingListRepository;

    @Autowired
    private PickingItemRepository pickingItemRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CoreServiceClient coreServiceClient;

    private PickingList pickingList;
    private PickingItem pickingItem1;
    private PickingItem pickingItem2;
    private OutboundOrderItem orderItem1;
    private OutboundOrderItem orderItem2;
    private OutboundOrder order;

    @BeforeEach
    void setUp() {
        // Create Sales Order
        order = OutboundOrder.builder()
                .orderNumber("ORD-PACK-01")
                .companyId(1L)
                .warehouseLocationId(1L)
                .customerId(1L)
                .orderDate(LocalDateTime.now())
                .status(OutboundOrderStatus.PICKING)
                .shippingAddressId(1L)
                .build();

        orderItem1 = OutboundOrderItem.builder()
                .outboundOrder(order)
                .productCode("PROD-X")
                .quantity(new BigDecimal("5.0000"))
                .allocatedQuantity(new BigDecimal("5.0000"))
                .pickedQuantity(BigDecimal.ZERO)
                .build();

        orderItem2 = OutboundOrderItem.builder()
                .outboundOrder(order)
                .productCode("PROD-Y")
                .quantity(new BigDecimal("10.0000"))
                .allocatedQuantity(new BigDecimal("10.0000"))
                .pickedQuantity(BigDecimal.ZERO)
                .build();

        order.setItems(new java.util.ArrayList<>(List.of(orderItem1, orderItem2)));
        outboundOrderRepository.save(order);

        // Create Picking List
        pickingList = PickingList.builder()
                .warehouseLocationId(1L)
                .companyId(1L)
                .createdByUserId(1L)
                .status(PickingListStatus.IN_PROGRESS)
                .build();

        pickingItem1 = PickingItem.builder()
                .pickingList(pickingList)
                .outboundOrderItem(orderItem1)
                .sourceLocationId(1L)
                .addressCode("A-01-01-01")
                .quantityToPick(new BigDecimal("5.0000"))
                .pickedQuantity(BigDecimal.ZERO)
                .status(PickingItemStatus.PENDING)
                .build();

        pickingItem2 = PickingItem.builder()
                .pickingList(pickingList)
                .outboundOrderItem(orderItem2)
                .sourceLocationId(1L)
                .addressCode("A-01-01-02")
                .quantityToPick(new BigDecimal("10.0000"))
                .pickedQuantity(BigDecimal.ZERO)
                .status(PickingItemStatus.PENDING)
                .build();

        pickingList.setItems(new java.util.ArrayList<>(List.of(pickingItem1, pickingItem2)));
        pickingListRepository.save(pickingList);
        TenantContextHolder.setContext(new TenantContext(1L, 1L, 1L));

        when(coreServiceClient.enforceWorkflowStep(anyString(), anyString(), any(), anyString()))
                .thenReturn(new WorkflowEnforceResponse(
                        WorkflowEnforceResponse.Decision.BYPASSED, null, null, "OUTBOUND", "PACKING"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void verifyPackingItem_shouldIncrementPickedQuantitySuccessfully() {
        // Act: Scan 2 units of PROD-X (out of 5)
        PackingVerifyResponse response = packingService.verifyPackingItem(new PackingVerifyRequest(
                pickingList.getId(), "PROD-X", new BigDecimal("2.0000")
        ));

        // Assert
        assertThat(response.productCode()).isEqualTo("PROD-X");
        assertThat(response.pickedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(response.warningMessage()).contains("Eksik okutma");

        // Verify in db
        PickingItem updatedItem = pickingItemRepository.findById(pickingItem1.getId()).orElseThrow();
        assertThat(updatedItem.getPickedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(updatedItem.getStatus()).isEqualTo(PickingItemStatus.PENDING);
    }

    @Test
    void verifyPackingItem_shouldTransitionStatusToPickedWhenFullyVerified() {
        // Act: Scan full 5 units of PROD-X
        PackingVerifyResponse response = packingService.verifyPackingItem(new PackingVerifyRequest(
                pickingList.getId(), "PROD-X", new BigDecimal("5.0000")
        ));

        // Assert
        assertThat(response.productCode()).isEqualTo("PROD-X");
        assertThat(response.pickedQuantity()).isEqualByComparingTo("5.0000");
        assertThat(response.status()).isEqualTo("PICKED");
        assertThat(response.warningMessage()).isNull();
    }

    @Test
    void verifyPackingItem_shouldThrowExceptionOnOverScanning() {
        // Act: Scan 6 units of PROD-X (limit is 5)
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            packingService.verifyPackingItem(new PackingVerifyRequest(
                    pickingList.getId(), "PROD-X", new BigDecimal("6.0000")
            ));
        });

        // Assert
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("aşıyor");
    }

    @Test
    void verifyPackingItem_shouldThrowExceptionWhenBarcodeNotFound() {
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            packingService.verifyPackingItem(new PackingVerifyRequest(
                    pickingList.getId(), "INVALID-PRODUCT", BigDecimal.ONE
            ));
        });

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("bulunmuyor");
    }

    @Test
    void closeBox_shouldFailWhenShortagesExist() {
        // Arrange: Scan only 5 units of PROD-X, leaving PROD-Y unscanned
        packingService.verifyPackingItem(new PackingVerifyRequest(
                pickingList.getId(), "PROD-X", new BigDecimal("5.0000")
        ));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            packingService.closeBox(pickingList.getId());
        });

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("Eksik okutulmuş");

        // Verify status transitioned to SHORTAGE
        PickingItem itemY = pickingItemRepository.findById(pickingItem2.getId()).orElseThrow();
        assertThat(itemY.getStatus()).isEqualTo(PickingItemStatus.SHORTAGE);
    }

    @Test
    void closeBox_shouldCompletePackingSuccessfullyAndWriteToOutbox() throws Exception {
        // Arrange: Scan all items fully
        packingService.verifyPackingItem(new PackingVerifyRequest(
                pickingList.getId(), "PROD-X", new BigDecimal("5.0000")
        ));
        packingService.verifyPackingItem(new PackingVerifyRequest(
                pickingList.getId(), "PROD-Y", new BigDecimal("10.0000")
        ));

        // Act
        CloseBoxResponse response = packingService.closeBox(pickingList.getId());

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.sscc()).hasSize(18); // Valid 18-digit SSCC
        assertThat(response.status()).isEqualTo("PACKED");

        // Verify DB statuses
        PickingList closedList = pickingListRepository.findById(pickingList.getId()).orElseThrow();
        assertThat(closedList.getStatus()).isEqualTo(PickingListStatus.COMPLETED);

        OutboundOrder packedOrder = outboundOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(packedOrder.getStatus()).isEqualTo(OutboundOrderStatus.PACKED);

        // Verify Outbox Message
        List<OutboxMessage> outboxMessages = outboxMessageRepository.findAll();
        assertThat(outboxMessages).hasSize(1);
        
        OutboxMessage outboxMessage = outboxMessages.get(0);
        assertThat(outboxMessage.getAggregateType()).isEqualTo("OutboundOrder");
        assertThat(outboxMessage.getAggregateId()).isEqualTo(order.getId());
        assertThat(outboxMessage.getStatus()).isEqualTo(OutboxStatus.PENDING);

        // Verify payload JSON deserialization
        OrderPackedEvent event = objectMapper.readValue(outboxMessage.getPayload(), OrderPackedEvent.class);
        assertThat(event.orderNumber()).isEqualTo("ORD-PACK-01");
        assertThat(event.sscc()).isEqualTo(response.sscc());
        assertThat(event.items()).hasSize(2);
    }
}
