package com.wms.inbound.repository;

import com.wms.inbound.InboundPostgresTestBase;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderItem;
import com.wms.inbound.entity.enums.InboundOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InboundOrderRepository} entegrasyon testleri — gerçek PostgreSQL 16 (Testcontainers).
 *
 * <p>order_number tekilliği, {@code findByOrderNumber} ve LEFT JOIN FETCH ile satırların
 * eager yüklenmesi ({@code findByIdWithItems}) üretim şeması üzerinde doğrulanır. Docker gerektirir.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InboundOrderRepositoryIT extends InboundPostgresTestBase {

    @Autowired
    private InboundOrderRepository repository;
    @Autowired
    private TestEntityManager em;

    private InboundOrder buildOrder(String orderNumber, int itemCount) {
        InboundOrder order = InboundOrder.builder()
                .orderNumber(orderNumber)
                .companyId(1L)
                .warehouseLocationId(10L)
                .supplierName("ACME Tedarik")
                .orderDate(LocalDateTime.now())
                .status(InboundOrderStatus.PENDING)
                .build();
        for (int i = 0; i < itemCount; i++) {
            InboundOrderItem item = InboundOrderItem.builder()
                    .inboundOrder(order)
                    .productCode("PROD-" + i)
                    .quantity(new BigDecimal("10.0000"))
                    .receivedQuantity(BigDecimal.ZERO)
                    .uom("EA")
                    .unitVolume(new BigDecimal("1.0000"))
                    .unitWeight(new BigDecimal("2.0000"))
                    .build();
            order.getItems().add(item);
        }
        return order;
    }

    @Test
    @DisplayName("findByOrderNumber — sipariş numarasıyla bulunur")
    void findByOrderNumber() {
        repository.saveAndFlush(buildOrder("PO-100", 2));
        em.clear();

        Optional<InboundOrder> found = repository.findByOrderNumber("PO-100");

        assertThat(found).isPresent();
        assertThat(found.get().getSupplierName()).isEqualTo("ACME Tedarik");
    }

    @Test
    @DisplayName("findByIdWithItems — JOIN FETCH ile satırlar eager gelir")
    void findByIdWithItems_fetchesItems() {
        InboundOrder saved = repository.saveAndFlush(buildOrder("PO-101", 3));
        em.clear();

        Optional<InboundOrder> found = repository.findByIdWithItems(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(3);
        assertThat(found.get().getItems())
                .extracting(InboundOrderItem::getProductCode)
                .containsExactlyInAnyOrder("PROD-0", "PROD-1", "PROD-2");
    }

    @Test
    @DisplayName("order_number tekilliği — çift kayıt reddedilir")
    void orderNumberUniqueness() {
        repository.saveAndFlush(buildOrder("PO-DUP", 1));
        assertThatThrownBy(() -> repository.saveAndFlush(buildOrder("PO-DUP", 1)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("createdAt @PrePersist ile otomatik atanır")
    void createdAtAutoAssigned() {
        InboundOrder saved = repository.saveAndFlush(buildOrder("PO-102", 0));
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
