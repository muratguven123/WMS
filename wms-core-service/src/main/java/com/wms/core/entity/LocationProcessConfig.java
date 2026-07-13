package com.wms.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Belirli bir lokasyonun belirli bir süreç tanımını etkinleştirip
 * etkinleştirmediğini (ve ek meta verilerini) tutar.
 * Bir lokasyon, aynı süreç tanımı için yalnızca bir konfigürasyon satırına sahip olabilir.
 */
@Entity
@Table(
    name = "location_process_configs",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_loc_proc_config",
        columnNames = {"location_id", "process_definition_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationProcessConfig extends BaseEntity {

    /**
     * Konfigürasyonun ait olduğu lokasyon.
     * Long olarak tutulur; cross-service FK gerektirmemek için loose coupling.
     */
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "process_definition_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_loc_proc_config_process_def")
    )
    private ProcessDefinition processDefinition;

    @OneToMany(mappedBy = "locationProcessConfig", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LocationProcessStepConfig> stepConfigs = new ArrayList<>();
}
