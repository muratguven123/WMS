package com.wms.core.dto.workflow;

import com.wms.core.entity.enums.ErrorStrategy;


/**
 * Lokasyon süreç adımı konfigürasyonu güncelleme isteği.
 * Null alanlar değiştirilmez.
 */
public record UpdateStepConfigRequest(
        Integer sequence,
        Boolean mandatory,
        Boolean active,
        Boolean requiresApproval,
        Long responsibleRoleId,
        ErrorStrategy errorStrategy
) {
}
