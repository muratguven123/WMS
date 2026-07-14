package com.wms.localization.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Şablonun dile özgü içeriğini ekleme/güncelleme isteği.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertTemplateContentRequest {

    /** EMAIL için zorunludur, SMS için null olmalıdır. PUSH için isteğe bağlıdır. */
    private String subject;

    @NotBlank(message = "Şablon gövdesi boş olamaz")
    private String body;
}
