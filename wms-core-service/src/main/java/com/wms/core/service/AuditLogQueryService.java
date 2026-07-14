package com.wms.core.service;

import com.wms.core.dto.audit.AuditLogEntryDto;
import com.wms.core.entity.ConfigurationAuditLog;
import com.wms.core.repository.ConfigurationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final ConfigurationAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogEntryDto> search(String entityName, Long changedByUserId, Pageable pageable) {
        Page<ConfigurationAuditLog> page;
        if (entityName != null && !entityName.isBlank() && changedByUserId != null) {
            page = auditLogRepository.findByEntityNameContainingIgnoreCaseAndChangedByUserIdOrderByChangedAtDesc(
                    entityName.trim(), changedByUserId, pageable);
        } else if (entityName != null && !entityName.isBlank()) {
            page = auditLogRepository.findByEntityNameContainingIgnoreCaseOrderByChangedAtDesc(
                    entityName.trim(), pageable);
        } else if (changedByUserId != null) {
            page = auditLogRepository.findByChangedByUserIdOrderByChangedAtDesc(changedByUserId, pageable);
        } else {
            page = auditLogRepository.findAllByOrderByChangedAtDesc(pageable);
        }
        return page.map(this::toDto);
    }

    private AuditLogEntryDto toDto(ConfigurationAuditLog log) {
        return new AuditLogEntryDto(
                log.getId(),
                log.getEntityName(),
                log.getEntityId(),
                log.getActionType(),
                log.getChangedFieldName(),
                log.getOldValue(),
                log.getNewValue(),
                log.getChangedByUserId(),
                log.getChangedAt());
    }
}
