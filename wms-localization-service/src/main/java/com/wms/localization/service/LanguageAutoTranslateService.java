package com.wms.localization.service;

import com.wms.localization.entity.Language;
import com.wms.localization.repository.LanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Otomatik çeviri tetikleyici — iş her zaman arka planda çalışır.
 */
@Service
@RequiredArgsConstructor
public class LanguageAutoTranslateService {

    private final LanguageRepository languageRepository;
    private final LanguageTranslateRunner translateRunner;

    /**
     * Çeviriyi arka planda başlatır. Her zaman -1 döner (async).
     *
     * @param sourceOverride opsiyonel kaynak dil kodu; null ise en dolu kaynak otomatik seçilir
     */
    public int autoTranslate(String langCode, boolean force, String sourceOverride) {
        Language lang = languageRepository.findByCode(langCode.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Dil bulunamadı: " + langCode));

        if (lang.isDefault()) {
            throw new IllegalArgumentException("Varsayılan dil çevrilemez");
        }

        if (sourceOverride != null && !sourceOverride.isBlank()) {
            languageRepository.findByCode(sourceOverride.toLowerCase())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Kaynak dil bulunamadı: " + sourceOverride));
        }

        translateRunner.schedule(lang.getCode(), force, sourceOverride);
        return -1;
    }

    public int autoTranslate(String langCode, boolean force) {
        return autoTranslate(langCode, force, null);
    }
}
