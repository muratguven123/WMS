package com.wms.core.dto.ui;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kural motoru tarafından çözümlenmiş ekran şeması — frontend'e gönderilecek nihai JSON.
 *
 * <p>Frontend bu DTO'yu alır ve her {@link ResolvedFieldDto} için
 * davranışına göre uygun input bileşenini çizer. Statik form kodu yoktur;
 * her form şeması bağlam (context) bazında dinamik üretilir.</p>
 *
 * @param screenCode  Ekran kodu — örn: {@code REC_CONTROL_FORM}
 * @param screenName  Ekran adı (gösterim için)
 * @param fields      Çözümlenmiş alan listesi
 * @param resolvedAt  Şemanın üretildiği zaman damgası (cache/debug amaçlı)
 */
public record ResolvedScreenDto(
        String screenCode,
        String screenName,
        String screenNameKey,
        List<ResolvedFieldDto> fields,
        OffsetDateTime resolvedAt
) {
    public static String toScreenNameKey(String screenCode) {
        return "screen." + screenCode + ".name";
    }
}
