package com.wms.core.entity.listener;

import com.wms.core.entity.ProcessStepDefinition;
import com.wms.core.entity.audit.ProcessStepDefinitionSnapshot;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ProcessStepDefinitionAuditListener {

    private static ApplicationEventPublisher eventPublisher;

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher publisher) {
        ProcessStepDefinitionAuditListener.eventPublisher = publisher;
    }

    @PostLoad
    public void captureSnapshot(ProcessStepDefinition entity) {
        entity.setAuditSnapshot(new ProcessStepDefinitionSnapshot(
                entity.getCode(),
                entity.getName(),
                entity.getDefaultSequence(),
                entity.isActive()
        ));
    }

    @PreUpdate
    public void publishChangeEvent(ProcessStepDefinition entity) {
        ProcessStepDefinitionSnapshot snapshot = entity.getAuditSnapshot();
        if (snapshot == null || eventPublisher == null) {
            return;
        }

        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();

        if (!snapshot.code().equals(entity.getCode())) {
            changes.add(new ConfigChangeEvent.FieldChange("code", snapshot.code(), entity.getCode()));
        }
        if (!snapshot.name().equals(entity.getName())) {
            changes.add(new ConfigChangeEvent.FieldChange("name", snapshot.name(), entity.getName()));
        }
        if (snapshot.defaultSequence() != entity.getDefaultSequence()) {
            changes.add(new ConfigChangeEvent.FieldChange("defaultSequence", String.valueOf(snapshot.defaultSequence()), String.valueOf(entity.getDefaultSequence())));
        }
        if (snapshot.active() != entity.isActive()) {
            changes.add(new ConfigChangeEvent.FieldChange("isActive", String.valueOf(snapshot.active()), String.valueOf(entity.isActive())));
        }

        if (!changes.isEmpty()) {
            Long userId = TenantContextHolder.getContext().map(TenantContext::userId).orElse(null);
            eventPublisher.publishEvent(new ConfigChangeEvent(
                    this,
                    ProcessStepDefinition.class.getSimpleName(),
                    entity.getId(),
                    "UPDATE",
                    changes,
                    userId
            ));
        }
    }
}
