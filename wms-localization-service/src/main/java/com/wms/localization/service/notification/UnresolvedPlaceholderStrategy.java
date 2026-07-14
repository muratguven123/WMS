package com.wms.localization.service.notification;

/**
 * Çözülemeyen {{placeholder}} için uygulanacak strateji.
 *
 * Varsayılan strateji application.yml'den okunur:
 * {@code wms.notification.template.unresolved-placeholder-strategy}
 * Render/preview isteklerinde istek bazında override edilebilir.
 *
 * Her durumda çözülemeyen placeholder'lar loglanır ve
 * {@code RenderedTemplateDto.unresolvedPlaceholders} listesinde raporlanır.
 */
public enum UnresolvedPlaceholderStrategy {

    /** Placeholder boş string ile değiştirilir (varsayılan). */
    BLANK,

    /** Placeholder olduğu gibi bırakılır — {{key}} metinde görünür kalır (debug için). */
    KEEP,

    /** PlaceholderResolutionException fırlatılır — kritik bildirimlerde eksik veri gönderimini engeller. */
    FAIL
}
