package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Sistem konfigürasyonu değişikliklerinin değişmez denetim kaydı.
 *
 * <p>Her kayıt tek bir alan değişikliğini temsil eder.
 * Bir güncelleme işleminde N alan değiştiyse N satır yazılır.</p>
 *
 * <p>Bu entity kasıtlı olarak {@link BaseEntity}'yi extend <b>etmez</b>:
 * audit log'lar asla güncellenemez veya soft-delete edilemez.</p>
 */
@Entity
@Table(
    name = "configuration_audit_logs",
    indexes = {
        @Index(name = "idx_audit_entity",    columnList = "entity_name, entity_id"),
        @Index(name = "idx_audit_changed_by", columnList = "changed_by_user_id"),
        @Index(name = "idx_audit_changed_at", columnList = "changed_at")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Değişikliğin gerçekleştiği entity sınıf adı — örn: LocationProcessStepConfig */
    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    /** Değişikliğin gerçekleştiği entity kaydının PK değeri */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** UPDATE, ACTIVATE, DEACTIVATE */
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    /** Değişen alan adı — örn: sequence, isMandatory */
    @Column(name = "changed_field_name", nullable = false, length = 100)
    private String changedFieldName;

    /** Değişiklik öncesi değer (String olarak) */
    @Column(name = "old_value", length = 500)
    private String oldValue;

    /** Değişiklik sonrası değer (String olarak) */
    @Column(name = "new_value", length = 500)
    private String newValue;

    /** Değişikliği yapan kullanıcının Long'si; sistem tarafından yapılan değişikliklerde null */
    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;
}
