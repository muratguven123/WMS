package com.wms.core.entity;

import com.wms.core.entity.audit.AuditSnapshot;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.entity.listener.LocationProcessStepConfigAuditListener;
import jakarta.persistence.*;
import lombok.*;


/**
 * Bir lokasyon süreç konfigürasyonu ({@link LocationProcessConfig}) içindeki
 * her adımın lokasyon bazlı özelleştirilmiş davranışını tutar.
 *
 * <p>Benzersizlik Kuralı: Aynı akış içinde iki farklı adım aynı {@code sequence}
 * numarasına sahip olamaz → {@code (locationProcessConfig, sequence)} UNIQUE.</p>
 *
 * <p>{@link LocationProcessStepConfigAuditListener} aracılığıyla tüm güncellemeler
 * otomatik olarak denetim kaydına alınır.</p>
 */
@Entity
@Table(
    name = "location_process_step_configs",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_loc_step_config_seq",
        columnNames = {"location_process_config_id", "sequence"}
    )
)
@EntityListeners(LocationProcessStepConfigAuditListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationProcessStepConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "location_process_config_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_step_config_loc_proc_config")
    )
    private LocationProcessConfig locationProcessConfig;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "process_step_definition_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_step_config_step_def")
    )
    private ProcessStepDefinition processStepDefinition;

    /** Bu lokasyona özgü adım sırası — null olamaz, akış içinde benzersiz olmalı. */
    @Column(name = "sequence", nullable = false)
    private int sequence;

    /**
     * Bu adımın zorunlu olup olmadığını belirtir.
     * false ise operatör adımı atlayabilir.
     */
    @Column(name = "is_mandatory", nullable = false)
    private boolean isMandatory = true;

    /** Nullable — belirtilmezse herhangi bir yetkili kullanabilir. */
    @Column(name = "responsible_role_id")
    private Long responsibleRoleId;

    /** Adımın bir yönetici onayı gerektirip gerektirmediğini belirtir. */
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = false;

    /** Adım başarısız olduğunda sistemin izleyeceği strateji. */
    @Enumerated(EnumType.STRING)
    @Column(name = "error_strategy", nullable = false, length = 30)
    private ErrorStrategy errorStrategy = ErrorStrategy.BLOCK;

    // -----------------------------------------------------------------------
    // Audit snapshot — JPA tarafından persist/merge edilmez
    // -----------------------------------------------------------------------

    /**
     * Entity veritabanından yüklendiğinde {@link LocationProcessStepConfigAuditListener}
     * tarafından doldurulan anlık görüntü. {@code @PreUpdate}'de eski değerleri
     * karşılaştırarak değişen alanları tespit etmek için kullanılır.
     */
    @Transient
    private AuditSnapshot auditSnapshot;
}
