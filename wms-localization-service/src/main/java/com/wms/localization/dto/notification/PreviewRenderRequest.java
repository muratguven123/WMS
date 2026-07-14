package com.wms.localization.dto.notification;

import com.wms.localization.service.notification.UnresolvedPlaceholderStrategy;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

/**
 * Yönetim panelinden örnek değişkenlerle şablon önizleme isteği.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewRenderRequest {

    @NotBlank(message = "languageCode boş olamaz")
    private String languageCode;

    private Map<String, Object> variables;

    private UnresolvedPlaceholderStrategy strategy;
}
