package com.wms.integration.repository;

import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.enums.IntegrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IntegrationLogRepository
        extends JpaRepository<IntegrationLog, Long>,
                JpaSpecificationExecutor<IntegrationLog> {

    /**
     * Outbox Worker kullanımı: RETRYING durumundaki kayıtları retry sayısına
     * göre sıralar, satır bazlı kilitler (Pessimistic Write = SELECT FOR UPDATE).
     *
     * <p>SKIP LOCKED ile diğer Worker node'larının zaten işlediği satırlar
     * atlanır; bu sayede yatay ölçekleme sağlanır.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT l FROM IntegrationLog l
            WHERE l.status = :status
              AND l.retryCount < :maxRetry
            ORDER BY l.retryCount ASC, l.createdAt ASC
            """)
    List<IntegrationLog> findRetryableWithLock(
            @Param("status") IntegrationStatus status,
            @Param("maxRetry") int maxRetry,
            Pageable pageable);

    /**
     * Force Retry API: belirli bir lokasyon konfigürasyonu için
     * FAILED kayıtları sayfalı olarak listeler.
     */
    Page<IntegrationLog> findByLocationIntegrationConfig_IdAndStatus(
            Long configId, IntegrationStatus status, Pageable pageable);

    /**
     * 30 günlük temizleme job'ı: eski SUCCESS logları siler.
     * FAILED kayıtlar operasyonel inceleme için saklanır.
     */
    @Modifying
    @Query("""
            DELETE FROM IntegrationLog l
            WHERE l.status = 'SUCCESS'
              AND l.createdAt < :cutoff
            """)
    int deleteOldSuccessLogs(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Dashboard / monitoring: son N gün içindeki log sayılarını duruma göre gruplar.
     */
    @Query("""
            SELECT l.status AS status, COUNT(l) AS count
            FROM IntegrationLog l
            WHERE l.createdAt >= :since
            GROUP BY l.status
            """)
    List<StatusCountProjection> countByStatusSince(@Param("since") OffsetDateTime since);

    Optional<IntegrationLog> findByOutboxMessageId(Long outboxMessageId);

    // -----------------------------------------------------------------------
    // Projections
    // -----------------------------------------------------------------------

    interface StatusCountProjection {
        IntegrationStatus getStatus();
        Long getCount();
    }
}
