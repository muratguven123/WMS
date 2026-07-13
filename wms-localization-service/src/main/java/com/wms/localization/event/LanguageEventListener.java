package com.wms.localization.event;

import com.wms.localization.entity.Language;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.service.LanguageTranslateRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Yeni dil eklendiğinde UI çevirilerini arka planda oluşturur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguageEventListener {

    private final LanguageRepository languageRepository;
    private final LanguageTranslateRunner translateRunner;

    @EventListener
    public void onLanguageCreated(LanguageCreatedEvent event) {
        Language newLang = languageRepository.findById(event.getNewLanguage().getId())
                .orElse(event.getNewLanguage());
        log.info("Yeni dil event'i → {}, otomatik çeviri kuyruğa alınıyor", newLang.getCode());
        translateRunner.schedule(newLang.getCode(), false);
    }
}
