package com.wms.localization.dto.notification;

import java.util.List;

/**
 * Render edilmiş bildirim şablonu yanıtı.
 *
 * @param subject                Çözümlenmiş başlık — SMS şablonlarında null
 * @param body                   Çözümlenmiş gövde
 * @param languageCode           İçeriğin fiilen render edildiği dil
 *                               (fallback uygulandıysa varsayılan dil)
 * @param fallbackApplied        İstenen dil bulunamayıp varsayılan dile düşüldü mü?
 * @param unresolvedPlaceholders Çözülemeyen placeholder anahtarları —
 *                               boş liste ideal durumdur
 */
public record RenderedTemplateDto(
        String subject,
        String body,
        String languageCode,
        boolean fallbackApplied,
        List<String> unresolvedPlaceholders
) {}
