package com.wms.core.dto.ui;

import com.wms.core.entity.enums.FieldBehavior;
import com.wms.core.entity.enums.FieldDataType;

/**
 * Kural motoru tarafından çözümlenmiş tek bir form alanının frontend'e
 * gönderilecek nihai durumu.
 *
 * <p>Frontend bu DTO'yu alarak alanı dinamik olarak render eder:</p>
 * <ul>
 *   <li>{@code HIDDEN}    → alan DOM'a eklenmez</li>
 *   <li>{@code READ_ONLY} → disabled input</li>
 *   <li>{@code MANDATORY} → required input + sunucu regex validasyonu</li>
 *   <li>{@code OPTIONAL}  → normal input</li>
 * </ul>
 *
 * @param fieldKey                   Alan anahtarı — çeviri sistemi bu değerle etiketi çeker
 * @param labelKey                   i18n çeviri anahtarı: {@code field.<fieldKey>.label}
 * @param dataType                   Frontend bileşen seçimi için veri tipi
 * @param behavior                   Çözümlenmiş davranış (kural motoru sonucu)
 * @param defaultValue               Varsa önceden dolu gösterilecek değer
 * @param validationRegex            Client-side ipucu olarak gönderilen regex (isteğe bağlı)
 * @param validationErrorMessageKey  Regex başarısızlık mesajı için i18n anahtarı
 */
public record ResolvedFieldDto(
        String fieldKey,
        String labelKey,
        FieldDataType dataType,
        FieldBehavior behavior,
        String defaultValue,
        String validationRegex,
        String validationErrorMessageKey
) {

    /**
     * Alan etiketinin çeviri anahtarını türetir.
     * Kural: {@code field.<fieldKey>.label}
     */
    public static String toLabelKey(String fieldKey) {
        return "field." + fieldKey + ".label";
    }
}
