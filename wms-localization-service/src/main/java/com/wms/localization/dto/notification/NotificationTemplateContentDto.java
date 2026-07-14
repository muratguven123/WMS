package com.wms.localization.dto.notification;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Bildirim şablonunun dile özgü içeriğini taşıyan veri transfer nesnesi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateContentDto {
    private Long id;
    private String languageCode;
    private String subject;
    private String body;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
