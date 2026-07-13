package com.wms.finance.repository;

import com.wms.finance.entity.TaxRate;
import com.wms.finance.entity.enums.TaxRateAuditActionType;
import com.wms.finance.entity.TaxRateAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaxRateAuditLogRepository extends JpaRepository<TaxRateAuditLog, Long> {

    List<TaxRateAuditLog> findByTaxRate_IdOrderByChangedAtDesc(Long taxRateId);

    List<TaxRateAuditLog> findByUserIdOrderByChangedAtDesc(Long userId);

    @Query("""
            SELECT a FROM TaxRateAuditLog a
            WHERE a.actionType = :actionType
              AND a.changedAt >= :from
              AND a.changedAt < :to
            ORDER BY a.changedAt DESC
            """)
    List<TaxRateAuditLog> findByActionTypeAndDateRange(
            @Param("actionType") TaxRateAuditActionType actionType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
