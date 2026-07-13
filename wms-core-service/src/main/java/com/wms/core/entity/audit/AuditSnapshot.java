package com.wms.core.entity.audit;

import com.wms.core.entity.enums.ErrorStrategy;


/**
 * {@code LocationProcessStepConfig} entity'sinin veritabanından yüklendiği
 * andaki alan değerlerini tutan değişmez anlık görüntü.
 */
public record AuditSnapshot(
        int sequence,
        boolean mandatory,
        boolean active,
        boolean requiresApproval,
        ErrorStrategy errorStrategy,
        Long responsibleRoleId
) {}