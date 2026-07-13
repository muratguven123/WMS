package com.wms.core.repository;

import com.wms.core.entity.ConfigurationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigurationAuditLogRepository extends JpaRepository<ConfigurationAuditLog, Long> {

    /** Belirli bir entity kaydına ait tüm audit logları zaman sırasına göre getirir. */
    List<ConfigurationAuditLog> findByEntityNameAndEntityIdOrderByChangedAtDesc(
            String entityName, Long entityId);

    org.springframework.data.domain.Page<ConfigurationAuditLog> findAllByOrderByChangedAtDesc(
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<ConfigurationAuditLog> findByEntityNameContainingIgnoreCaseOrderByChangedAtDesc(
            String entityName, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<ConfigurationAuditLog> findByChangedByUserIdOrderByChangedAtDesc(
            Long changedByUserId, org.springframework.data.domain.Pageable pageable);
}
