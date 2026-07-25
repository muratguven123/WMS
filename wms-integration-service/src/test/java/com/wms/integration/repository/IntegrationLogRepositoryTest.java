package com.wms.integration.repository;

import com.wms.integration.IntegrationPostgresTestBase;
import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.IntegrationSystem;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.entity.enums.ConnectionType;
import com.wms.integration.entity.enums.IntegrationDirection;
import com.wms.integration.entity.enums.IntegrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IntegrationLogRepository} gerçek-PostgreSQL entegrasyon testi.
 *
 * <p>Outbox Worker'ın çekirdek sorgusu ({@code findRetryableWithLock} — pessimistic write),
 * lokasyon+durum bazlı sayfalı listeleme, durum sayacı projeksiyonu ({@code countByStatusSince})
 * ve 30 günlük temizleme ({@code deleteOldSuccessLogs} — @Modifying) üretim şemasına karşı
 * doğrulanır. createdAt TIMESTAMPTZ (@CreationTimestamp) olduğundan yalnız gerçek PostgreSQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("IntegrationLogRepository (gerçek PostgreSQL)")
class IntegrationLogRepositoryTest extends IntegrationPostgresTestBase {

    @Autowired
    private IntegrationLogRepository logRepository;
    @Autowired
    private IntegrationSystemRepository systemRepository;
    @Autowired
    private IntegrationJobRepository jobRepository;
    @Autowired
    private LocationIntegrationConfigRepository configRepository;
    @Autowired
    private TestEntityManager em;

    private LocationIntegrationConfig config;
    private IntegrationJob job;

    @BeforeEach
    void setUp() {
        IntegrationSystem system = IntegrationSystem.builder()
                .code("LOGSYS_" + System.nanoTime())
                .name("Log Test ERP")
                .build();
        system = systemRepository.saveAndFlush(system);

        config = LocationIntegrationConfig.builder()
                .locationId(950000L)
                .integrationSystem(system)
                .connectionType(ConnectionType.REST)
                .connectionParams(Map.of("baseUrl", "https://erp.test"))
                .build();
        config = configRepository.saveAndFlush(config);

        job = jobRepository.saveAndFlush(IntegrationJob.builder()
                .code("LOGJOB_" + System.nanoTime())
                .name("Log Test Job")
                .direction(IntegrationDirection.OUTBOUND)
                .build());
    }

    private IntegrationLog persistLog(IntegrationStatus status, int retryCount, Long outboxMessageId) {
        IntegrationLog log = IntegrationLog.builder()
                .locationIntegrationConfig(config)
                .integrationJob(job)
                .status(status)
                .retryCount(retryCount)
                .outboxMessageId(outboxMessageId)
                .requestPayload("{\"x\":1}")
                .build();
        return logRepository.saveAndFlush(log);
    }

    @Test
    @DisplayName("findByOutboxMessageId — outbox ilişkisiyle bulunur")
    void findByOutboxMessageId() {
        persistLog(IntegrationStatus.SUCCESS, 0, 555L);
        em.clear();

        assertThat(logRepository.findByOutboxMessageId(555L)).isPresent();
        assertThat(logRepository.findByOutboxMessageId(999999L)).isEmpty();
    }

    @Test
    @DisplayName("findByLocationIntegrationConfig_IdAndStatus — config+durum sayfalı filtre")
    void findByConfigAndStatus_paged() {
        persistLog(IntegrationStatus.FAILED, 1, 1L);
        persistLog(IntegrationStatus.FAILED, 2, 2L);
        persistLog(IntegrationStatus.SUCCESS, 0, 3L);
        em.clear();

        Page<IntegrationLog> failed = logRepository.findByLocationIntegrationConfig_IdAndStatus(
                config.getId(), IntegrationStatus.FAILED, PageRequest.of(0, 10));

        assertThat(failed.getTotalElements()).isEqualTo(2);
        assertThat(failed.getContent()).allMatch(l -> l.getStatus() == IntegrationStatus.FAILED);
    }

    @Test
    @DisplayName("countByStatusSince — duruma göre gruplu sayım projeksiyonu")
    void countByStatusSince() {
        persistLog(IntegrationStatus.FAILED, 1, 10L);
        persistLog(IntegrationStatus.FAILED, 1, 11L);
        persistLog(IntegrationStatus.SUCCESS, 0, 12L);
        em.clear();

        List<IntegrationLogRepository.StatusCountProjection> counts =
                logRepository.countByStatusSince(OffsetDateTime.now().minusMinutes(5));

        long failed = counts.stream()
                .filter(c -> c.getStatus() == IntegrationStatus.FAILED)
                .mapToLong(IntegrationLogRepository.StatusCountProjection::getCount)
                .sum();
        assertThat(failed).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("deleteOldSuccessLogs — SUCCESS silinir, FAILED korunur")
    void deleteOldSuccessLogs() {
        Long successId = persistLog(IntegrationStatus.SUCCESS, 0, 20L).getId();
        Long failedId = persistLog(IntegrationStatus.FAILED, 1, 21L).getId();

        // cutoff geleceğe alınır → yeni oluşturulan SUCCESS kaydı da kapsanır (mekanizma testi).
        int deleted = logRepository.deleteOldSuccessLogs(OffsetDateTime.now().plusDays(1));
        em.clear();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(logRepository.findById(successId)).isEmpty();
        assertThat(logRepository.findById(failedId)).isPresent();
    }

    @Test
    @DisplayName("findRetryableWithLock — RETRYING kayıtları retryCount'a göre sıralı kilitler")
    void findRetryableWithLock() {
        Long retry0 = persistLog(IntegrationStatus.RETRYING, 0, 30L).getId();
        Long retry1 = persistLog(IntegrationStatus.RETRYING, 1, 31L).getId();
        persistLog(IntegrationStatus.RETRYING, 5, 32L); // maxRetry sınırının üstünde → hariç
        em.clear();

        List<IntegrationLog> retryable = logRepository.findRetryableWithLock(
                IntegrationStatus.RETRYING, 3, PageRequest.of(0, 10));

        List<Long> myOrdered = retryable.stream()
                .map(IntegrationLog::getId)
                .filter(id -> id.equals(retry0) || id.equals(retry1))
                .toList();
        // retryCount ASC → retry0, retry1 sırası korunur; retryCount=5 olan hariç kalır.
        assertThat(myOrdered).containsExactly(retry0, retry1);
        assertThat(retryable).noneMatch(l -> l.getRetryCount() >= 3);
    }
}
