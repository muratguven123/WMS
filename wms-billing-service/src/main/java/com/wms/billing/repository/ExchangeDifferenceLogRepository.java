package com.wms.billing.repository;

import com.wms.billing.domain.entity.ExchangeDifferenceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExchangeDifferenceLogRepository extends JpaRepository<ExchangeDifferenceLog, Long> {

    List<ExchangeDifferenceLog> findByInvoiceId(Long invoiceId);

    List<ExchangeDifferenceLog> findByInvoiceIdOrderByCalculationDateDesc(Long invoiceId);

    List<ExchangeDifferenceLog> findByCalculationDateBetween(LocalDateTime start, LocalDateTime end);
}
