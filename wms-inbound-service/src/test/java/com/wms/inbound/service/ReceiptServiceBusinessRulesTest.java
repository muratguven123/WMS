package com.wms.inbound.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.dto.ReceiptApprovedEvent;
import com.wms.inbound.dto.ReceiptItemQcRequest;
import com.wms.inbound.dto.ReceiptItemRequest;
import com.wms.inbound.dto.ReceiptQcRequest;
import com.wms.inbound.dto.ReceiptResponse;
import com.wms.inbound.dto.StartReceiptRequest;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderItem;
import com.wms.inbound.entity.Receipt;
import com.wms.inbound.entity.ReceiptItem;
import com.wms.inbound.entity.enums.InboundOrderStatus;
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
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReceiptService} iş kuralı / hata-dalı testleri — mevcut {@code ReceiptServiceTest}'i
 * tamamlar (happy-path'leri tekrarlamaz).
 *
 * <p>Kapsanan kritik davranışlar: sipariş/ürün doğrulama hataları, QC durum geçiş muhafızları,
 * onayda kısmi ve karışık (PASSED/FAILED) QC sonuçlarının stok/event'e doğru yansıması ve
 * transactional outbox serileştirme hatasının 500'e sarılması. Saf Mockito — Docker gerektirmez.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceBusinessRulesTest {

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

    private static final Long ORDER_ID = 1L;
    private static final Long WAREHOUSE_ID = 1L;

    private InboundOrder inboundOrder;
    private InboundOrderItem orderItem;

    @BeforeEach
    void setUp() {
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
                .id(ORDER_ID)
                .orderNumber("PO-2026-0001")
                .companyId(1L)
                .warehouseLocationId(WAREHOUSE_ID)
                .supplierName("Test Supplier")
                .orderDate(LocalDateTime.now())
                .status(InboundOrderStatus.PENDING)
                .items(new ArrayList<>(List.of(orderItem)))
                .build();
        orderItem.setInboundOrder(inboundOrder);

        TenantContextHolder.setContext(new TenantContext(1L, 1L, WAREHOUSE_ID));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private Receipt receiptWith(ReceiptStatus status, ReceiptItem... items) {
        Receipt receipt = Receipt.builder()
                .id(1L)
                .inboundOrder(inboundOrder)
                .receiptNumber("REC-1001")
                .receivedByUserId(1L)
                .receivedAt(LocalDateTime.now())
                .status(status)
                .build();
        List<ReceiptItem> list = new ArrayList<>(List.of(items));
        list.forEach(i -> i.setReceipt(receipt));
        receipt.setItems(list);
        return receipt;
    }

    private static ReceiptItem item(String product, String qty, ReceiptItemQcStatus qc) {
        return ReceiptItem.builder()
                .productCode(product)
                .quantity(new BigDecimal(qty))
                .qcStatus(qc)
                .build();
    }

    // ---------- startReceipt ----------

    @Test
    @DisplayName("startReceipt — sipariş bulunamazsa NOT_FOUND")
    void startReceipt_orderNotFound() {
        when(inboundOrderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.empty());
        StartReceiptRequest request = new StartReceiptRequest(
                ORDER_ID, "REC-1001", 1L,
                List.of(new ReceiptItemRequest("PROD_001", new BigDecimal("5"))));

        assertThatThrownBy(() -> receiptService.startReceipt(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("startReceipt — iptal edilmiş sipariş BAD_REQUEST")
    void startReceipt_cancelledOrder() {
        inboundOrder.setStatus(InboundOrderStatus.CANCELLED);
        when(inboundOrderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(inboundOrder));
        StartReceiptRequest request = new StartReceiptRequest(
                ORDER_ID, "REC-1001", 1L,
                List.of(new ReceiptItemRequest("PROD_001", new BigDecimal("5"))));

        assertThatThrownBy(() -> receiptService.startReceipt(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("startReceipt — siparişe ait olmayan ürün kodu BAD_REQUEST")
    void startReceipt_productNotInOrder() {
        when(inboundOrderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(inboundOrder));
        when(receiptRepository.findByReceiptNumber("REC-1001")).thenReturn(Optional.empty());
        StartReceiptRequest request = new StartReceiptRequest(
                ORDER_ID, "REC-1001", 1L,
                List.of(new ReceiptItemRequest("UNKNOWN", new BigDecimal("5"))));

        assertThatThrownBy(() -> receiptService.startReceipt(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");
    }

    // ---------- inputQcResults ----------

    @Test
    @DisplayName("inputQcResults — QC_PENDING dışındaki fişte BAD_REQUEST")
    void inputQc_notPending() {
        Receipt receipt = receiptWith(ReceiptStatus.APPROVED, item("PROD_001", "5", ReceiptItemQcStatus.PASSED));
        when(receiptRepository.findWithDetailsById(1L)).thenReturn(Optional.of(receipt));
        ReceiptQcRequest req = new ReceiptQcRequest(
                List.of(new ReceiptItemQcRequest("PROD_001", ReceiptItemQcStatus.PASSED, "L", "S")));

        assertThatThrownBy(() -> receiptService.inputQcResults(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("inputQcResults — fişte olmayan ürün kodu BAD_REQUEST")
    void inputQc_productNotInReceipt() {
        Receipt receipt = receiptWith(ReceiptStatus.QC_PENDING, item("PROD_001", "5", ReceiptItemQcStatus.PENDING));
        when(receiptRepository.findWithDetailsById(1L)).thenReturn(Optional.of(receipt));
        ReceiptQcRequest req = new ReceiptQcRequest(
                List.of(new ReceiptItemQcRequest("OTHER", ReceiptItemQcStatus.PASSED, "L", "S")));

        assertThatThrownBy(() -> receiptService.inputQcResults(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found in receipt");
    }

    // ---------- approveReceipt ----------

    @Test
    @DisplayName("approveReceipt — QC_PENDING olmayan fiş onaylanamaz")
    void approve_notPending() {
        Receipt receipt = receiptWith(ReceiptStatus.APPROVED, item("PROD_001", "5", ReceiptItemQcStatus.PASSED));
        when(receiptRepository.findWithDetailsById(1L)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> receiptService.approveReceipt(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only QC_PENDING");
    }

    @Test
    @DisplayName("approveReceipt — hiç PASSED kalem yoksa BAD_REQUEST")
    void approve_allFailed() {
        Receipt receipt = receiptWith(ReceiptStatus.QC_PENDING,
                item("PROD_001", "5", ReceiptItemQcStatus.FAILED));
        when(receiptRepository.findWithDetailsById(1L)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> receiptService.approveReceipt(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At least one item must pass");
    }

    @Test
    @DisplayName("approveReceipt — kısmi teslim siparişi RECEIVING'de bırakır, öneri yoksa null")
    void approve_partialReceipt_keepsReceiving() {
        Receipt receipt = receiptWith(ReceiptStatus.QC_PENDING,
                item("PROD_001", "5", ReceiptItemQcStatus.PASSED)); // sipariş 10, teslim 5 → kısmi
        when(receiptRepository.findWithDetailsById(1L)).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(putawayEngineService.findPutawayLocation(any(), eq(WAREHOUSE_ID))).thenReturn(Optional.empty());

        ReceiptResponse response = receiptService.approveReceipt(1L);

        assertThat(response.status()).isEqualTo(ReceiptStatus.APPROVED);
        assertThat(orderItem.getReceivedQuantity()).isEqualByComparingTo("5.0000");
        assertThat(inboundOrder.getStatus()).isEqualTo(InboundOrderStatus.RECEIVING);

        ArgumentCaptor<ReceiptApprovedEvent> captor = ArgumentCaptor.forClass(ReceiptApprovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().items()).hasSize(1);
        assertThat(captor.getValue().items().get(0).recommendedStorageLocationId()).isNull();
    }

    @Test
    @DisplayName("approveReceipt — karışık QC: yalnız PASSED kalem stok/event'e yansır")
    void approve_mixedQc_onlyPassedPropagated() {
        InboundOrderItem secondOrderItem = InboundOrderItem.builder()
                .id(2L).productCode("PROD_002").quantity(new BigDecimal("10.0000"))
                .receivedQuantity(BigDecimal.ZERO).uom("PCS")
                .unitVolume(BigDecimal.ONE).unitWeight(BigDecimal.ONE)
                .inboundOrder(inboundOrder).build();
        inboundOrder.getItems().add(secondOrderItem);

        Receipt receipt = receiptWith(ReceiptStatus.QC_PENDING,
                item("PROD_001", "5", ReceiptItemQcStatus.PASSED),
                item("PROD_002", "5", ReceiptItemQcStatus.FAILED));
        when(receiptRepository.findWithDetailsById(1L)).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(putawayEngineService.findPutawayLocation(any(), eq(WAREHOUSE_ID))).thenReturn(Optional.of(7L));

        receiptService.approveReceipt(1L);

        // Yalnız PASSED kalem stok sayacını arttırır.
        assertThat(orderItem.getReceivedQuantity()).isEqualByComparingTo("5.0000");
        assertThat(secondOrderItem.getReceivedQuantity()).isEqualByComparingTo("0");

        ArgumentCaptor<ReceiptApprovedEvent> captor = ArgumentCaptor.forClass(ReceiptApprovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().items()).hasSize(1);
        assertThat(captor.getValue().items().get(0).productCode()).isEqualTo("PROD_001");
        assertThat(captor.getValue().items().get(0).recommendedStorageLocationId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("approveReceipt — outbox serileştirme hatası 500'e sarılır ve event yayınlanmaz")
    void approve_outboxSerializationFails() throws Exception {
        Receipt receipt = receiptWith(ReceiptStatus.QC_PENDING,
                item("PROD_001", "5", ReceiptItemQcStatus.PASSED));
        when(receiptRepository.findWithDetailsById(1L)).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(putawayEngineService.findPutawayLocation(any(), eq(WAREHOUSE_ID))).thenReturn(Optional.of(7L));
        doThrow(new RuntimeException("boom")).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> receiptService.approveReceipt(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
