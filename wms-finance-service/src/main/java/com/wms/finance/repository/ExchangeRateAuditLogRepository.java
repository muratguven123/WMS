package com.wms.finance.repository;

import com.wms.finance.entity.ExchangeRateAuditLog;
import com.wms.finance.entity.enums.AuditActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExchangeRateAuditLogRepository extends JpaRepository<ExchangeRateAuditLog, Long> {

    @Query("""
            SELECT al FROM ExchangeRateAuditLog al
            JOIN FETCH al.exchangeRate er
            WHERE er.id = :exchangeRateId
            ORDER BY al.changedAt DESC
            """)
    List<ExchangeRateAuditLog> findByExchangeRateId(@Param("exchangeRateId") Long exchangeRateId);

    @Query("""
            SELECT al FROM ExchangeRateAuditLog al
            JOIN FETCH al.exchangeRate er
            WHERE al.userId = :userId
            ORDER BY al.changedAt DESC
            """)
    Page<ExchangeRateAuditLog> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT al FROM ExchangeRateAuditLog al
            JOIN FETCH al.exchangeRate er
            WHERE al.actionType = :actionType
              AND al.changedAt BETWEEN :from AND :to
            ORDER BY al.changedAt DESC
            """)
    Page<ExchangeRateAuditLog> findByActionTypeAndDateRange(
            @Param("actionType") AuditActionType actionType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
