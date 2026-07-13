package com.wms.inbound.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.wms.inbound.security.TenantContext;
import com.wms.inbound.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private InboundOrderRepository inboundOrderRepository;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private PutawayEngineService putawayEngineService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReceiptService receiptService;

    private Long orderId;
    private Long warehouseLocationId;
    private InboundOrder inboundOrder;
    private InboundOrderItem orderItem;

    @BeforeEach
    void setUp() {
        orderId = 1L;
        warehouseLocationId = 1L;

        orderItem = InboundOrderItem.builder()
                .id(1L)
                .productCode("PROD_001")
                .quantity(new BigDecimal("10.0000"))
                .receivedQuantity(BigDecimal.ZERO)
                .uom("PCS")
                .unitVolume(BigDecimal.ONE)
                .unitWeight(BigDecimal.ONE)
                .build();

        inboundOrder = InboundOrder.builder()
                .id(orderId)
                .orderNumber("PO-2026-0001")
                .companyId(1L)
                .warehouseLocationId(warehouseLocationId)
                .supplierName("Test Supplier")
                .orderDate(LocalDateTime.now())
                .status(InboundOrderStatus.PENDING)
                .items(new ArrayList<>(List.of(orderItem)))
                .build();

        orderItem.setInboundOrder(inboundOrder);
        TenantContextHolder.setContext(new TenantContext(1L, 1L, warehouseLocationId));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void startReceipt_createsReceiptAndUpdatesOrderStatus() {
        StartReceiptRequest request = new StartReceiptRequest(
                orderId, "REC-1001", 1L,
                List.of(new ReceiptItemRequest("PROD_001", new BigDecimal("5.0000")))
        );

        when(inboundOrderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(inboundOrder));
        when(receiptRepository.findByReceiptNumber("REC-1001")).thenReturn(Optional.empty());
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReceiptResponse response = receiptService.startReceipt(request);

        assertThat(response.receiptNumber()).isEqualTo("REC-1001");
        assertThat(response.status()).isEqualTo(ReceiptStatus.QC_PENDING);
        assertThat(response.items().get(0).qcStatus()).isEqualTo(ReceiptItemQcStatus.PENDING);
        assertThat(inboundOrder.getStatus()).isEqualTo(InboundOrderStatus.RECEIVING);
        verify(inboundOrderRepository).save(inboundOrder);
    }

    @Test
    void startReceipt_orderCompleted_throwsException() {
        inboundOrder.setStatus(InboundOrderStatus.COMPLETED);
        StartReceiptRequest request = new StartReceiptRequest(
                orderId, "REC-1001", 1L,
                List.of(new ReceiptItemRequest("PROD_001", new BigDecimal("5.0000")))
        );
        when(inboundOrderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(inboundOrder));

        assertThatThrownBy(() -> receiptService.startReceipt(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void startReceipt_duplicateReceiptNumber_throwsException() {
        StartReceiptRequest request = new StartReceiptRequest(
                orderId, "REC-1001", 1L,
                List.of(new ReceiptItemRequest("PROD_001", new BigDecimal("5.0000")))
        );
        when(inboundOrderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(inboundOrder));
        when(receiptRepository.findByReceiptNumber("REC-1001"))
                .thenReturn(Optional.of(Receipt.builder().build()));

        assertThatThrownBy(() -> receiptService.startReceipt(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void inputQcResults_updatesItemQcDetails() {
        Long receiptId = 1L;
        Receipt receipt = Receipt.builder()
                .id(receiptId)
                .inboundOrder(inboundOrder)
                .receiptNumber("REC-1001")
                .status(ReceiptStatus.QC_PENDING)
                .build();

        ReceiptItem receiptItem = ReceiptItem.builder()
                .id(1L)
                .receipt(receipt)
                .productCode("PROD_001")
                .quantity(new BigDecimal("5.0000"))
                .qcStatus(ReceiptItemQcStatus.PENDING)
                .build();
        receipt.setItems(new ArrayList<>(List.of(receiptItem)));

        ReceiptQcRequest qcRequest = new ReceiptQcRequest(
                List.of(new ReceiptItemQcRequest("PROD_001", ReceiptItemQcStatus.PASSED, "LOT123", "SN999"))
        );

        when(receiptRepository.findWithDetailsById(receiptId)).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = receiptService.inputQcResults(receiptId, qcRequest);

        assertThat(response.items().get(0).qcStatus()).isEqualTo(ReceiptItemQcStatus.PASSED);
        assertThat(response.items().get(0).lotNumber()).isEqualTo("LOT123");
    }

    @Test
    void approveReceipt_pendingQc_throwsException() {
        Long receiptId = 1L;
        Receipt receipt = Receipt.builder()
                .id(receiptId)
                .inboundOrder(inboundOrder)
                .status(ReceiptStatus.QC_PENDING)
                .build();
        receipt.setItems(new ArrayList<>(List.of(
                ReceiptItem.builder()
                        .productCode("PROD_001")
                        .quantity(BigDecimal.ONE)
                        .qcStatus(ReceiptItemQcStatus.PENDING)
                        .build())));

        when(receiptRepository.findWithDetailsById(receiptId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> receiptService.approveReceipt(receiptId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("QC results");
    }

    @Test
    void approveReceipt_setsStatusApprovedAndCreatesOutbox() throws JsonProcessingException {
        Long receiptId = 1L;
        Receipt receipt = Receipt.builder()
                .id(receiptId)
                .inboundOrder(inboundOrder)
                .receiptNumber("REC-1001")
                .status(ReceiptStatus.QC_PENDING)
                .receivedByUserId(1L)
                .receivedAt(LocalDateTime.now())
                .build();

        ReceiptItem receiptItem = ReceiptItem.builder()
                .id(1L)
                .receipt(receipt)
                .productCode("PROD_001")
                .quantity(new BigDecimal("5.0000"))
                .qcStatus(ReceiptItemQcStatus.PASSED)
                .build();
        receipt.setItems(new ArrayList<>(List.of(receiptItem)));

        when(receiptRepository.findWithDetailsById(receiptId)).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(putawayEngineService.findPutawayLocation(any(), eq(warehouseLocationId)))
                .thenReturn(Optional.of(1L));

        ReceiptResponse response = receiptService.approveReceipt(receiptId);

        assertThat(response.status()).isEqualTo(ReceiptStatus.APPROVED);
        assertThat(orderItem.getReceivedQuantity()).isEqualByComparingTo("5.0000");

        ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxMessageRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPayload()).contains("REC-1001", "PROD_001");

        ArgumentCaptor<ReceiptApprovedEvent> eventCaptor = ArgumentCaptor.forClass(ReceiptApprovedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ReceiptApprovedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.receiptId()).isEqualTo(receiptId);
        assertThat(capturedEvent.inboundOrderId()).isEqualTo(orderId);
        assertThat(capturedEvent.companyId()).isEqualTo(inboundOrder.getCompanyId());
        assertThat(capturedEvent.warehouseLocationId()).isEqualTo(warehouseLocationId);
        assertThat(capturedEvent.items()).hasSize(1);
        assertThat(capturedEvent.items().get(0).productCode()).isEqualTo("PROD_001");
        assertThat(capturedEvent.items().get(0).quantity()).isEqualByComparingTo("5.0000");
        assertThat(capturedEvent.items().get(0).recommendedStorageLocationId()).isNotNull();
    }

    @Test
    void approveReceipt_fullyReceived_completesOrder() {
        Long receiptId = 1L;
        Receipt receipt = Receipt.builder()
                .id(receiptId)
                .inboundOrder(inboundOrder)
                .receiptNumber("REC-1001")
                .status(ReceiptStatus.QC_PENDING)
                .receivedByUserId(1L)
                .receivedAt(LocalDateTime.now())
                .build();

        ReceiptItem receiptItem = ReceiptItem.builder()
                .receipt(receipt)
                .productCode("PROD_001")
                .quantity(new BigDecimal("10.0000"))
                .qcStatus(ReceiptItemQcStatus.PASSED)
                .build();
        receipt.setItems(new ArrayList<>(List.of(receiptItem)));

        when(receiptRepository.findWithDetailsById(receiptId)).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        receiptService.approveReceipt(receiptId);

        assertThat(inboundOrder.getStatus()).isEqualTo(InboundOrderStatus.COMPLETED);
        verify(eventPublisher, times(1)).publishEvent(any(ReceiptApprovedEvent.class));
    }
}
