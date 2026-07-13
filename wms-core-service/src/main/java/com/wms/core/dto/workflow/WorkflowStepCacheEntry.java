package com.wms.core.dto.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wms.core.entity.enums.ErrorStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Redis'te saklanan iş akışı adımı önbellek girişi.
 * JPA entity'leri yerine bu POJO serialize edilir; Lazy-load tuzağından kaçınılır.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowStepCacheEntry {

    private Long stepConfigId;

    /** ProcessStepDefinition.code — örn: QC, SERIAL_CONTROL */
    private String stepCode;

    private String stepName;

    /** Lokasyon bazlı override sequence */
    private int sequence;

    private boolean mandatory;

    private boolean requiresApproval;

    private ErrorStrategy errorStrategy;

    /** nullable — belirlenmemişse herhangi bir yetkili kullanabilir */
    private Long responsibleRoleId;
}
