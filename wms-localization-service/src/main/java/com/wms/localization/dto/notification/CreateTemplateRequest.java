package com.wms.localization.dto.notification;

import com.wms.localization.entity.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Yeni bir bildirim şablonu oluşturma isteği.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTemplateRequest {

    @NotBlank(message = "Şablon kodu boş olamaz")
    @Size(max = 100, message = "Şablon kodu en fazla 100 karakter olabilir")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Şablon kodu büyük harf, rakam ve alt çizgiden oluşmalıdır (UPPER_SNAKE_CASE)")
    private String templateCode;

    @NotNull(message = "Gönderim kanalı boş olamaz")
    private NotificationChannel channel;

    private String description;

    @Builder.Default
    private boolean active = true;
}
