package com.wms.localization.dto.notification;

import com.wms.localization.entity.NotificationChannel;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bildirim şablonu genel bilgilerini taşıyan veri transfer nesnesi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateDto {
    private Long id;
    private String templateCode;
    private NotificationChannel channel;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
