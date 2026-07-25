package com.wms.inbound.repository;

import com.wms.inbound.InboundPostgresTestBase;
import com.wms.inbound.entity.OutboxMessage;
import com.wms.inbound.entity.enums.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OutboxMessageRepository} entegrasyon testleri — gerçek PostgreSQL 16 (Testcontainers).
 *
 * <p>Outbox pattern'in relayer sorgusu ({@code findByStatus(PENDING)}) ve {@code @PrePersist}
 * ile status/createdAt varsayılan atamaları doğrulanır. Bu sorgu, yayınlanmamış event'lerin
 * güvenilir teslimi için kritik olduğundan gerçek şemada test edilir. Docker gerektirir.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxMessageRepositoryIT extends InboundPostgresTestBase {

    @Autowired
    private OutboxMessageRepository repository;
    @Autowired
    private TestEntityManager em;

    private OutboxMessage message(String type, Long aggregateId, OutboxStatus status) {
        return OutboxMessage.builder()
                .aggregateType(type)
                .aggregateId(aggregateId)
                .payload("{\"k\":\"v\"}")
                .status(status)
                .build();
    }

    @Test
    @DisplayName("findByStatus(PENDING) — yalnız yayınlanmamış mesajları döner")
    void findByStatus_returnsOnlyPending() {
        repository.saveAndFlush(message("Receipt", 1L, OutboxStatus.PENDING));
        repository.saveAndFlush(message("Receipt", 2L, OutboxStatus.PENDING));
        repository.saveAndFlush(message("Receipt", 3L, OutboxStatus.PROCESSED));
        em.clear();

        List<OutboxMessage> pending = repository.findByStatus(OutboxStatus.PENDING);

        assertThat(pending).hasSize(2)
                .extracting(OutboxMessage::getAggregateId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("@PrePersist — status null ise PENDING'e ve createdAt'e atanır")
    void prePersistDefaults() {
        OutboxMessage saved = repository.saveAndFlush(message("Receipt", 9L, null));
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("PROCESSED durumu için PENDING sorgusu boş döner")
    void noPending_whenAllProcessed() {
        repository.saveAndFlush(message("Receipt", 5L, OutboxStatus.PROCESSED));
        em.clear();
        assertThat(repository.findByStatus(OutboxStatus.PENDING)).isEmpty();
    }
}
