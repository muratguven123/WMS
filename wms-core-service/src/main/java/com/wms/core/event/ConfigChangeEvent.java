package com.wms.core.event;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Bir konfigürasyon entity'sinde alan değişikliği gerçekleştiğinde
 * yayınlanan Spring ApplicationEvent.
 *
 * <p>JPA {@code @PreUpdate} callback'inden (istek thread'i) fırlatılır;
 * {@code AuditLogService} tarafından {@code @Async} olarak işlenir.
 * Bu sayede DB yazımı ana işlemi geciktirmez.</p>
 */
public class ConfigChangeEvent extends ApplicationEvent {

    private final String entityName;
    private final Long entityId;
    private final String actionType;
    private final List<FieldChange> changes;
    private final Long changedByUserId;

    public ConfigChangeEvent(Object source,
                             String entityName,
                             Long entityId,
                             String actionType,
                             List<FieldChange> changes,
                             Long changedByUserId) {
        super(source);
        this.entityName       = entityName;
        this.entityId         = entityId;
        this.actionType       = actionType;
        this.changes          = List.copyOf(changes); // immutable kopyası
        this.changedByUserId  = changedByUserId;
    }

    public String getEntityName()     { return entityName; }
    public Long getEntityId()         { return entityId; }
    public String getActionType()     { return actionType; }
    public List<FieldChange> getChanges() { return changes; }
    public Long getChangedByUserId()  { return changedByUserId; }

    // -----------------------------------------------------------------------
    // İç sınıf: tek bir alan değişikliğini temsil eder
    // -----------------------------------------------------------------------

    /**
     * Tek bir alan düzeyindeki değişikliği taşıyan değişmez veri sınıfı.
     *
     * @param fieldName alan adı — örn: sequence, isMandatory
     * @param oldValue  değişiklik öncesi String değeri
     * @param newValue  değişiklik sonrası String değeri
     */
    public record FieldChange(String fieldName, String oldValue, String newValue) {}
}
