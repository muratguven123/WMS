package com.wms.outbound.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.dto.InventoryIssueRequest;
import com.wms.outbound.dto.VerifyLoadResponse;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderItem;
import com.wms.outbound.entity.OutboxMessage;
import com.wms.outbound.entity.Shipment;
import com.wms.outbound.entity.ShipmentItem;
import com.wms.outbound.entity.enums.OutboundOrderStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentItemRepository shipmentItemRepository;

    @Mock
    private OutboundOrderRepository outboundOrderRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private InventoryServiceClient inventoryServiceClient;

    @Mock
    private CarrierIntegrationService carrierIntegrationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CoreServiceClient coreServiceClient;

    @InjectMocks
    private ShipmentService shipmentService;

    private Long shipmentId;
    private Long warehouseLocationId;
    private Shipment shipment;
    private ShipmentItem item1;
    private ShipmentItem item2;
    private Long orderId1;
    private Long orderId2;

    @BeforeEach
    void setUp() {
        shipmentId = 1L;
        warehouseLocationId = 1L;
        orderId1 = 1L;
        orderId2 = 2L;

        shipment = Shipment.builder()
                .id(shipmentId)
                .shipmentNumber("SH-TEST-100")
                .companyId(1L)
                .warehouseLocationId(warehouseLocationId)
                .status(ShipmentStatus.PENDING)
                .totalBoxes(2)
                .totalWeight(new BigDecimal("15.5000"))
                .items(new ArrayList<>())
                .build();

        item1 = ShipmentItem.builder()
                .id(1L)
                .shipment(shipment)
                .outboundOrderId(orderId1)
                .boxSsccNumber("SSCC-001")
                .status(ShipmentItemStatus.STAGED)
                .build();

        item2 = ShipmentItem.builder()
                .id(2L)
                .shipment(shipment)
                .outboundOrderId(orderId2)
                .boxSsccNumber("SSCC-002")
                .status(ShipmentItemStatus.STAGED)
                .build();

        shipment.getItems().add(item1);
        shipment.getItems().add(item2);
    }

    @Test
    void verifyLoad_shouldUpdateItemToLoaded_andParentToLoadedIfAllLoaded() {
        when(shipmentRepository.findWithDetailsById(shipmentId)).thenReturn(Optional.of(shipment));

        VerifyLoadResponse res1 = shipmentService.verifyLoad(shipmentId, "SSCC-001");

        assertThat(res1).isNotNull();
        assertThat(res1.boxSsccNumber()).isEqualTo("SSCC-001");
        assertThat(res1.status()).isEqualTo("LOADED");
        assertThat(res1.allLoaded()).isFalse();
        assertThat(item1.getStatus()).isEqualTo(ShipmentItemStatus.LOADED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDING);
        verify(shipmentItemRepository, times(1)).save(item1);

        VerifyLoadResponse res2 = shipmentService.verifyLoad(shipmentId, "SSCC-002");

        assertThat(res2.allLoaded()).isTrue();
        assertThat(item2.getStatus()).isEqualTo(ShipmentItemStatus.LOADED);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.LOADED);
        verify(shipmentItemRepository, times(1)).save(item2);
        verify(shipmentRepository, times(1)).save(shipment);
    }

    @Test
    void verifyLoad_shouldBeIdempotent_whenSsccAlreadyLoaded() {
        item1.setStatus(ShipmentItemStatus.LOADED);
        item2.setStatus(ShipmentItemStatus.LOADED);
        shipment.setStatus(ShipmentStatus.LOADED);
        when(shipmentRepository.findWithDetailsById(shipmentId)).thenReturn(Optional.of(shipment));

        VerifyLoadResponse response = shipmentService.verifyLoad(shipmentId, "SSCC-001");

        assertThat(response.allLoaded()).isTrue();
        verify(shipmentItemRepository, never()).save(any());
    }

    @Test
    void verifyLoad_shouldThrowNotFoundException_whenShipmentDoesNotExist() {
        when(shipmentRepository.findWithDetailsById(shipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentService.verifyLoad(shipmentId, "SSCC-001"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getMessage()).contains("Shipment not found");
                });
    }

    @Test
    void verifyLoad_shouldThrowBadRequestException_whenItemNotInShipment() {
        when(shipmentRepository.findWithDetailsById(shipmentId)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.verifyLoad(shipmentId, "SSCC-NONEXISTENT"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getMessage()).contains("not associated with this shipment");
                });
    }

    @Test
    void dispatch_shouldUpdateStatusToDispatched_andWriteToOutboxAndCallInventoryService() throws Exception {
        item1.setStatus(ShipmentItemStatus.LOADED);
        item2.setStatus(ShipmentItemStatus.LOADED);
        shipment.setStatus(ShipmentStatus.LOADED);

        OutboundOrder order1 = buildOrder(orderId1, "SKU-A", new BigDecimal("2"));
        OutboundOrder order2 = buildOrder(orderId2, "SKU-B", new BigDecimal("3"));

        when(shipmentRepository.findWithDetailsById(shipmentId)).thenReturn(Optional.of(shipment));
        when(outboundOrderRepository.findAllByIdWithItems(List.of(orderId1, orderId2)))
                .thenReturn(List.of(order1, order2));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mock\":\"event\"}");

        Shipment result = shipmentService.dispatch(shipmentId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(result.getDispatchedAt()).isNotNull();
        assertThat(order1.getStatus()).isEqualTo(OutboundOrderStatus.SHIPPED);
        assertThat(order2.getStatus()).isEqualTo(OutboundOrderStatus.SHIPPED);

        verify(shipmentRepository, times(1)).save(shipment);
        verify(outboundOrderRepository).saveAll(any());
        verify(inventoryServiceClient, times(1)).issueStock(any(InventoryIssueRequest.class));
        verify(outboxMessageRepository, times(1)).save(any(OutboxMessage.class));
    }

    @Test
    void dispatch_shouldThrowBadRequestException_whenSomeItemsAreStillStaged() {
        item1.setStatus(ShipmentItemStatus.LOADED);
        item2.setStatus(ShipmentItemStatus.STAGED);
        shipment.setStatus(ShipmentStatus.PENDING);

        when(shipmentRepository.findWithDetailsById(shipmentId)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> shipmentService.dispatch(shipmentId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getMessage()).contains("tum koliler yuklenene kadar arac cikisina izin verilmez");
                });

        verify(inventoryServiceClient, never()).issueStock(any());
        verify(outboxMessageRepository, never()).save(any());
    }

    private OutboundOrder buildOrder(Long orderId, String productCode, BigDecimal qty) {
        OutboundOrderItem orderItem = OutboundOrderItem.builder()
                .productCode(productCode)
                .quantity(qty)
                .pickedQuantity(qty)
                .allocatedQuantity(qty)
                .build();

        OutboundOrder order = OutboundOrder.builder()
                .id(orderId)
                .companyId(1L)
                .warehouseLocationId(1L)
                .status(OutboundOrderStatus.PACKED)
                .items(new ArrayList<>(List.of(orderItem)))
                .build();
        orderItem.setOutboundOrder(order);
        return order;
    }
}
