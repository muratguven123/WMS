package com.wms.localization.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Sistemde karşılığı bulunamayan bir çeviri anahtarı sorgulandığında
 * TranslationService tarafından yayınlanan event.
 *
 * Yayıncı : TranslationServiceImpl (tek anahtar sorgusunda)
 * Dinleyici: MissingTranslationAuditor
 */
@Getter
public class MissingTranslationEvent extends ApplicationEvent {

    private final String locale;
    private final String keyCode;
    /** Null olabilir — anahtar hiç tanımlı değilse module bilinmez */
    private final String module;

    public MissingTranslationEvent(Object source, String locale, String keyCode, String module) {
        super(source);
        this.locale  = locale;
        this.keyCode = keyCode;
        this.module  = module;
    }
}
