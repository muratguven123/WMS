package com.wms.integration.repository;

import com.wms.integration.entity.OutboxMessage;
import com.wms.integration.entity.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Outbox Worker'ın ana sorgusudur.
     *
     * <p>PENDING veya FAILED durumundaki, {@code nextAttemptAt} zamanı geçmiş
     * kayıtları {@code createdAt} sırasına göre çeker ve satır bazlı
     * {@code SELECT FOR UPDATE} kilidi uygular.
     *
     * <p>Yatay ölçekleme: Spring Data JPA SKIP LOCKED'ı doğrudan desteklemez;
     * gerektiğinde {@code @QueryHint} veya native query ile eklenebilir.
     * Tek node deployment'larda bu sorgu yeterlidir.
     *
     * @param statuses  işlenecek durum listesi (PENDING, FAILED)
     * @param now       zaman karşılaştırma referansı
     * @param pageable  batch boyutu (tipik: 50 kayıt)
     * @return kilitlenmiş outbox kayıtları
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m FROM OutboxMessage m
            WHERE m.status IN :statuses
              AND m.nextAttemptAt <= :now
            ORDER BY m.retryCount ASC, m.createdAt ASC
            """)
    List<OutboxMessage> findPendingWithLock(
            @Param("statuses") List<OutboxStatus> statuses,
            @Param("now") OffsetDateTime now,
            Pageable pageable);

    /**
     * 30 günlük temizleme job'ı: eski COMPLETED mesajları siler.
     * FAILED_MAX_RETRIES kayıtlar silinmez — arşiv olarak saklanır.
     */
    @Modifying
    @Query("""
            DELETE FROM OutboxMessage m
            WHERE m.status = 'COMPLETED'
              AND m.createdAt < :cutoff
            """)
    int deleteOldCompletedMessages(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Admin / Force Retry API: kalıcı hata durumundaki mesajları listeler.
     */
    List<OutboxMessage> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

    /**
     * Belirli bir aggregate için bekleyen mesaj var mı? (Idempotency kontrolü)
     */
    boolean existsByAggregateIdAndStatusIn(Long aggregateId, List<OutboxStatus> statuses);
}
