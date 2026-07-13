package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistemdeki iş akışı şablonlarını (INBOUND, OUTBOUND vb.) tanımlar.
 * Her lokasyon bu tanımlı süreçlerden birini konfigüre eder.
 */
@Entity
@Table(
    name = "process_definitions",
    uniqueConstraints = @UniqueConstraint(name = "uk_process_def_code", columnNames = "code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessDefinition extends BaseEntity {

    /**
     * İş akışının sistem kodu — örn: INBOUND, OUTBOUND, RETURN.
     */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @OneToMany(mappedBy = "processDefinition", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessStepDefinition> stepDefinitions = new ArrayList<>();

    @OneToMany(mappedBy = "processDefinition", fetch = FetchType.LAZY)
    @Builder.Default
    private List<LocationProcessConfig> locationConfigs = new ArrayList<>();
}
