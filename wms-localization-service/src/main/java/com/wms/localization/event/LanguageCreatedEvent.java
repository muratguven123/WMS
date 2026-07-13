package com.wms.localization.event;

import com.wms.localization.entity.Language;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Sisteme yeni bir dil eklendiğinde fırlatılan event.
 * Yayıncı: LanguageService.addLanguage()
 * Dinleyici: LanguageEventListener
 */
@Getter
public class LanguageCreatedEvent extends ApplicationEvent {

    private final Language newLanguage;

    public LanguageCreatedEvent(Object source, Language newLanguage) {
        super(source);
        this.newLanguage = newLanguage;
    }
}
