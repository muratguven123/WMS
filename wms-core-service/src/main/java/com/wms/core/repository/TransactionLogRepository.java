package com.wms.core.repository;

import com.wms.core.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TransactionLogRepository
        extends JpaRepository<TransactionLog, Long>,
                JpaSpecificationExecutor<TransactionLog> {

    List<TransactionLog> findByCompanyIdAndLocationId(Long companyId, Long locationId);

    List<TransactionLog> findByCompanyIdAndCreatedAtUtcBetween(
            Long companyId, OffsetDateTime start, OffsetDateTime end);
}
