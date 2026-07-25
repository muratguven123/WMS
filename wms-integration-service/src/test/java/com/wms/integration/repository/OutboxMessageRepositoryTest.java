package com.wms.integration.repository;

import com.wms.integration.IntegrationPostgresTestBase;
import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OutboxMessageRepository} gerçek-PostgreSQL entegrasyon testi.
 *
 * <p>Worker'ın ana sorgusu ({@code findPendingWithLock}), idempotency kontrolü
 * ({@code existsByAggregateIdAndStatusIn}) ve admin listeleme sorgusunu üretim
 * şemasına karşı doğrular. {@code nextAttemptAt}/{@code createdAt} TIMESTAMPTZ
 * (OffsetDateTime) olduğundan H2 ile değil yalnız gerçek PostgreSQL ile test edilir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("OutboxMessageRepository (gerçek PostgreSQL)")
class OutboxMessageRepositoryTest extends IntegrationPostgresTestBase {

    @Autowired
    private OutboxMessageRepository repository;

    private static OutboxMessage.OutboxMessageBuilder base(Long aggregateId, OutboxStatus status) {
        return OutboxMessage.builder()
                .aggregateType("InventoryMovement")
                .aggregateId(aggregateId)
                .jobCode("STOCK_MOVE")
                .payload("{\"qty\":10}")
                .locationId(1L)
                .status(status)
                .retryCount(0);
    }

    @Test
    @DisplayName("existsByAggregateIdAndStatusIn idempotency kontrolünü doğru yapar")
    void existsByAggregateIdAndStatusIn() {
        repository.save(base(100L, OutboxStatus.PENDING).build());

        assertThat(repository.existsByAggregateIdAndStatusIn(100L, List.of(OutboxStatus.PENDING))).isTrue();
        assertThat(repository.existsByAggregateIdAndStatusIn(100L, List.of(OutboxStatus.COMPLETED))).isFalse();
        assertThat(repository.existsByAggregateIdAndStatusIn(999L, List.of(OutboxStatus.PENDING))).isFalse();
    }

    @Test
    @DisplayName("findPendingWithLock yalnız vadesi gelmiş PENDING/FAILED kayıtları retryCount sırasıyla döner")
    void findPendingWithLockFiltersAndOrders() {
        OffsetDateTime now = OffsetDateTime.now();

        // Dahil: vadesi geçmiş, retryCount farklı (sıralama doğrulaması)
        repository.save(base(1L, OutboxStatus.FAILED).retryCount(2).nextAttemptAt(now.minusMinutes(5)).build());
        repository.save(base(2L, OutboxStatus.PENDING).retryCount(0).nextAttemptAt(now.minusMinutes(5)).build());
        // Hariç: COMPLETED
        repository.save(base(3L, OutboxStatus.COMPLETED).nextAttemptAt(now.minusMinutes(5)).build());
        // Hariç: vadesi gelecekte
        repository.save(base(4L, OutboxStatus.PENDING).nextAttemptAt(now.plusMinutes(30)).build());
        repository.flush();

        List<OutboxMessage> due = repository.findPendingWithLock(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED), now, PageRequest.of(0, 50));

        assertThat(due).extracting(OutboxMessage::getAggregateId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("findByStatusOrderByCreatedAtDesc kalıcı hata kayıtlarını listeler")
    void findByStatusOrderByCreatedAtDesc() {
        repository.save(base(10L, OutboxStatus.FAILED_MAX_RETRIES).build());
        repository.save(base(11L, OutboxStatus.FAILED_MAX_RETRIES).build());
        repository.flush();

        List<OutboxMessage> failed = repository.findByStatusOrderByCreatedAtDesc(
                OutboxStatus.FAILED_MAX_RETRIES, PageRequest.of(0, 50));

        assertThat(failed).hasSize(2)
                .allSatisfy(m -> assertThat(m.getStatus()).isEqualTo(OutboxStatus.FAILED_MAX_RETRIES));
    }
}
