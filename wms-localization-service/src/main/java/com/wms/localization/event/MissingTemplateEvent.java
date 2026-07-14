package com.wms.localization.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * İstenen dilde içeriği bulunamayan bir bildirim şablonu render edildiğinde
 * NotificationTemplateRenderService tarafından yayınlanan event.
 *
 * Yayıncı  : NotificationTemplateRenderService (içerik çözümleme sırasında)
 * Dinleyici: MissingTemplateAuditor
 *
 * Not: Varsayılan dile fallback başarılı olsa bile istenen dil eksikse
 * event yayınlanır — kapsama raporunun tam olması için.
 */
@Getter
public class MissingTemplateEvent extends ApplicationEvent {

    private final String locale;
    private final String templateCode;
    /** Null olabilir — denormalize kanal bilgisi */
    private final String channel;

    public MissingTemplateEvent(Object source, String locale, String templateCode, String channel) {
        super(source);
        this.locale       = locale;
        this.templateCode = templateCode;
        this.channel      = channel;
    }
}
