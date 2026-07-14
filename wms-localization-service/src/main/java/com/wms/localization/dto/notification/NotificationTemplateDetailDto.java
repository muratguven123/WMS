package com.wms.localization.dto.notification;

import com.wms.localization.entity.NotificationChannel;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Şablonun tüm detaylarını ve dillerdeki içeriklerini bir arada sunan DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateDetailDto {
    private Long id;
    private String templateCode;
    private NotificationChannel channel;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<NotificationTemplateContentDto> contents;
}
