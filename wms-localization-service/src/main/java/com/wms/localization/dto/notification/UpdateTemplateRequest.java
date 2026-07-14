package com.wms.localization.dto.notification;

import lombok.*;

/**
 * Bildirim şablonu güncelleme isteği.
 * templateCode ve channel yayınlandıktan sonra değiştirilemez.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTemplateRequest {

    private String description;

    private boolean active;
}
