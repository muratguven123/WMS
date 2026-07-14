package com.wms.core.entity;

import com.wms.core.entity.audit.ProcessStepDefinitionSnapshot;
import com.wms.core.entity.listener.ProcessStepDefinitionAuditListener;
import jakarta.persistence.*;
import lombok.*;

/**
 * Bir süreç tanımı içindeki adım şablonlarını tutar.
 * Örn: QC, SERIAL_CONTROL, CUSTOMS_CONTROL.
 * defaultSequence, bu adımın sistem genelindeki varsayılan sıralamasıdır;
 * lokasyon bazlı override için {@link LocationProcessStepConfig} kullanılır.
 */
@Entity
@Table(
    name = "process_step_definitions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_step_def_process_code",
        columnNames = {"process_definition_id", "code"})
)
@EntityListeners(ProcessStepDefinitionAuditListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessStepDefinition extends BaseEntity {

    @Transient
    private ProcessStepDefinitionSnapshot auditSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "process_definition_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_step_def_process_def")
    )
    private ProcessDefinition processDefinition;

    /**
     * Adımın sistem kodu — örn: QC, SERIAL_CONTROL.
     */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Sistem genelinde önerilen varsayılan sıra numarası.
     * Lokasyon konfigürasyonunda bu değer override edilebilir.
     */
    @Column(name = "default_sequence", nullable = false)
    private int defaultSequence;
}
