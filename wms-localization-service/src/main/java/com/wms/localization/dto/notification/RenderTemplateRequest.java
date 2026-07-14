package com.wms.localization.dto.notification;

import com.wms.localization.service.notification.UnresolvedPlaceholderStrategy;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

/**
 * Diğer mikroservisler tarafından yapılan internal render istek gövdesi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenderTemplateRequest {

    @NotBlank(message = "templateCode boş olamaz")
    private String templateCode;

    @NotBlank(message = "languageCode boş olamaz")
    private String languageCode;

    private Map<String, Object> variables;

    private UnresolvedPlaceholderStrategy strategy;
}
