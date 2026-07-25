package com.wms.inbound.repository;

import com.wms.inbound.InboundPostgresTestBase;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.Receipt;
import com.wms.inbound.entity.ReceiptItem;
import com.wms.inbound.entity.enums.InboundOrderStatus;
import com.wms.inbound.entity.enums.ReceiptItemQcStatus;
import com.wms.inbound.entity.enums.ReceiptStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReceiptRepository} entegrasyon testleri — gerçek PostgreSQL 16 (Testcontainers).
 *
 * <p>@EntityGraph ile nested fetch (items + inboundOrder + inboundOrder.items), status'e ve
 * tenant (company + warehouse) alanlarına göre sayfalı sorgular ve receipt_number tekilliği
 * üretim şeması üzerinde doğrulanır — H2'nin taklit edemediği davranışlar dahil. Docker gerektirir.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReceiptRepositoryIT extends InboundPostgresTestBase {

    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private InboundOrderRepository inboundOrderRepository;
    @Autowired
    private TestEntityManager em;

    private InboundOrder persistOrder(String orderNumber, Long companyId, Long warehouseId) {
        InboundOrder order = InboundOrder.builder()
                .orderNumber(orderNumber)
                .companyId(companyId)
                .warehouseLocationId(warehouseId)
                .supplierName("ACME Tedarik")
                .orderDate(LocalDateTime.now())
                .status(InboundOrderStatus.RECEIVING)
                .build();
        return inboundOrderRepository.saveAndFlush(order);
    }

    private Receipt persistReceipt(InboundOrder order, String receiptNumber, ReceiptStatus status) {
        Receipt receipt = Receipt.builder()
                .inboundOrder(order)
                .receiptNumber(receiptNumber)
                .receivedByUserId(99L)
                .receivedAt(LocalDateTime.now())
                .status(status)
                .build();
        ReceiptItem item = ReceiptItem.builder()
                .receipt(receipt)
                .productCode("PROD-1")
                .quantity(new BigDecimal("5.0000"))
                .qcStatus(ReceiptItemQcStatus.PENDING)
                .build();
        receipt.getItems().add(item);
        return receiptRepository.saveAndFlush(receipt);
    }

    @Test
    @DisplayName("findByReceiptNumber — kayıt numarasıyla bulunur")
    void findByReceiptNumber() {
        InboundOrder order = persistOrder("PO-1", 1L, 10L);
        persistReceipt(order, "RC-1", ReceiptStatus.QC_PENDING);
        em.clear();

        Optional<Receipt> found = receiptRepository.findByReceiptNumber("RC-1");

        assertThat(found).isPresent();
        assertThat(found.get().getReceiptNumber()).isEqualTo("RC-1");
    }

    @Test
    @DisplayName("findWithDetailsById — EntityGraph ile items ve inboundOrder eager yüklenir")
    void findWithDetailsById_fetchesGraph() {
        InboundOrder order = persistOrder("PO-2", 1L, 10L);
        Receipt saved = persistReceipt(order, "RC-2", ReceiptStatus.QC_PENDING);
        em.clear();

        Optional<Receipt> found = receiptRepository.findWithDetailsById(saved.getId());

        assertThat(found).isPresent();
        // Persistence context temizlendi; graph eager çekilmeli — items ve order erişilebilir olmalı.
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().get(0).getProductCode()).isEqualTo("PROD-1");
        assertThat(found.get().getInboundOrder().getOrderNumber()).isEqualTo("PO-2");
    }

    @Test
    @DisplayName("findByStatus — duruma göre sayfalı filtre")
    void findByStatus_paged() {
        InboundOrder order = persistOrder("PO-3", 1L, 10L);
        persistReceipt(order, "RC-3A", ReceiptStatus.QC_PENDING);
        persistReceipt(order, "RC-3B", ReceiptStatus.APPROVED);
        em.clear();

        Page<Receipt> pending = receiptRepository.findByStatus(
                ReceiptStatus.QC_PENDING, PageRequest.of(0, 10));

        assertThat(pending.getContent())
                .extracting(Receipt::getReceiptNumber)
                .containsExactly("RC-3A");
    }

    @Test
    @DisplayName("Tenant-scoped sorgu — company + warehouse'a göre izole eder")
    void findByCompanyAndWarehouse_isolatesTenant() {
        InboundOrder t1 = persistOrder("PO-T1", 1L, 100L);
        InboundOrder t2 = persistOrder("PO-T2", 2L, 200L);
        persistReceipt(t1, "RC-T1", ReceiptStatus.QC_PENDING);
        persistReceipt(t2, "RC-T2", ReceiptStatus.QC_PENDING);
        em.clear();

        Page<Receipt> tenant1 = receiptRepository
                .findByInboundOrder_CompanyIdAndInboundOrder_WarehouseLocationId(
                        1L, 100L, PageRequest.of(0, 10));

        assertThat(tenant1.getContent())
                .extracting(Receipt::getReceiptNumber)
                .containsExactly("RC-T1");
    }

    @Test
    @DisplayName("Tenant-scoped + status — company/warehouse ve duruma göre filtre")
    void findByCompanyWarehouseAndStatus() {
        InboundOrder order = persistOrder("PO-4", 1L, 100L);
        persistReceipt(order, "RC-4A", ReceiptStatus.QC_PENDING);
        persistReceipt(order, "RC-4B", ReceiptStatus.APPROVED);
        em.clear();

        Page<Receipt> approved = receiptRepository
                .findByInboundOrder_CompanyIdAndInboundOrder_WarehouseLocationIdAndStatus(
                        1L, 100L, ReceiptStatus.APPROVED, PageRequest.of(0, 10));

        assertThat(approved.getContent())
                .extracting(Receipt::getReceiptNumber)
                .containsExactly("RC-4B");
    }

    @Test
    @DisplayName("receipt_number tekilliği — aynı numara ikinci kez kaydedilemez")
    void receiptNumberUniqueness() {
        InboundOrder order = persistOrder("PO-5", 1L, 10L);
        persistReceipt(order, "RC-DUP", ReceiptStatus.QC_PENDING);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                persistReceipt(order, "RC-DUP", ReceiptStatus.QC_PENDING))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Bilinmeyen numara için sonuç boş")
    void findByReceiptNumber_missing() {
        List<Receipt> all = receiptRepository.findAll();
        assertThat(receiptRepository.findByReceiptNumber("YOK")).isEmpty();
        assertThat(all).isNotNull();
    }
}
