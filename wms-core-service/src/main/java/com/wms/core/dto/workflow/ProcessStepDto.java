package com.wms.core.dto.workflow;

import com.wms.core.entity.enums.ErrorStrategy;
import lombok.Builder;


/**
 * {@code WorkflowValidatorService.determineNextStep} metodunun dönüş tipi.
 *
 * <p>{@code processCompleted = true} ise sırada başka aktif adım kalmamıştır
 * ve süreç COMPLETED kabul edilir. Bu durumda diğer alanlar null/default olabilir.</p>
 */
@Builder
public record ProcessStepDto(

        /** Adımın sistem kodu — örn: QC, SHIPPING */
        String stepCode,

        String stepName,

        /** Lokasyon bazlı sıra numarası */
        int sequence,

        /** true ise bu adım atlanamaz */
        boolean mandatory,

        /** true ise bir yönetici onayı beklenmelidir */
        boolean requiresApproval,

        /** Adım başarısız olduğunda uygulanacak strateji */
        ErrorStrategy errorStrategy,

        /** nullable — belirlenmemişse herhangi yetkili tamamlayabilir */
        Long responsibleRoleId,

        /**
         * true ise sırada aktif adım kalmamıştır; süreç tamamlandı.
         * Bu bayrak true olduğunda diğer alanlar anlamlı değildir.
         */
        boolean processCompleted
) {

    /** Süreç tamamlandı sinyali için factory metodu. */
    public static ProcessStepDto completed() {
        return ProcessStepDto.builder()
                .processCompleted(true)
                .build();
    }
}
