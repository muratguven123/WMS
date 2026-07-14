package com.wms.localization.service.notification;

import com.wms.localization.dto.ActiveFormatResponse;
import com.wms.localization.dto.notification.RenderedTemplateDto;
import com.wms.localization.entity.NotificationTemplate;
import com.wms.localization.entity.NotificationTemplateContent;
import com.wms.localization.event.MissingTemplateEvent;
import com.wms.localization.exception.notification.NotificationTemplateNotFoundException;
import com.wms.localization.exception.notification.TemplateContentNotFoundException;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.NotificationTemplateContentRepository;
import com.wms.localization.repository.NotificationTemplateRepository;
import com.wms.localization.security.TenantContext;
import com.wms.localization.security.TenantContextHolder;
import com.wms.localization.service.FormatConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bildirim şablonu render servisi.
 * Diğer servislerden gelen render isteklerini veya admin panelindeki önizleme isteklerini işler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationTemplateRenderService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateContentRepository contentRepository;
    private final LanguageRepository languageRepository;
    private final FormatConfigService formatConfigService;
    private final TemplatePlaceholderResolver placeholderResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${wms.notification.template.unresolved-placeholder-strategy:BLANK}")
    private UnresolvedPlaceholderStrategy defaultStrategy;

    /**
     * Standard rendering for client services (only processes active templates).
     */
    public RenderedTemplateDto render(String templateCode, String languageCode, Map<String, Object> variables) {
        return renderInternal(templateCode, languageCode, variables, null, true);
    }

    /**
     * Rendering with explicit placeholder resolution strategy (only processes active templates).
     */
    public RenderedTemplateDto render(String templateCode, String languageCode, Map<String, Object> variables, UnresolvedPlaceholderStrategy strategy) {
        return renderInternal(templateCode, languageCode, variables, strategy, true);
    }

    /**
     * Core internal rendering logic.
     * Supports both active-only checks (standard render) and passive templates (admin preview).
     */
    public RenderedTemplateDto renderInternal(String templateCode,
                                             String languageCode,
                                             Map<String, Object> variables,
                                             UnresolvedPlaceholderStrategy strategy,
                                             boolean onlyActive) {
        if (templateCode == null || templateCode.isBlank()) {
            throw new IllegalArgumentException("Şablon kodu boş olamaz");
        }
        if (languageCode == null || languageCode.isBlank()) {
            throw new IllegalArgumentException("Dil kodu boş olamaz");
        }

        String normalizedLanguage = languageCode.toLowerCase(Locale.ROOT);

        Optional<NotificationTemplate> templateOpt = onlyActive
                ? templateRepository.findByTemplateCodeAndActiveTrue(templateCode)
                : templateRepository.findByTemplateCode(templateCode);

        NotificationTemplate template = templateOpt
                .orElseThrow(() -> NotificationTemplateNotFoundException.byCode(templateCode));

        // 1. İstenen dilde şablon içeriğini ara
        Optional<NotificationTemplateContent> contentOpt =
                contentRepository.findByTemplate_IdAndLanguageCode(template.getId(), normalizedLanguage);

        NotificationTemplateContent content;
        boolean fallbackApplied = false;
        String finalLanguage = normalizedLanguage;

        if (contentOpt.isPresent()) {
            content = contentOpt.get();
        } else {
            // İstenen dil bulunamadı -> MissingTemplateEvent yayınla
            publishMissingTemplateEvent(normalizedLanguage, templateCode, template.getChannel().name());

            // Varsayılan dili bul
            String defaultLangCode = languageRepository.findFirstByIsDefaultTrueAndIsActiveTrue()
                    .map(l -> l.getCode().toLowerCase(Locale.ROOT))
                    .orElse("tr");

            if (defaultLangCode.equalsIgnoreCase(normalizedLanguage)) {
                // Zaten varsayılan dildeysek, fallback yapacak başka dil yoktur
                throw new TemplateContentNotFoundException(templateCode, normalizedLanguage, defaultLangCode);
            }

            Optional<NotificationTemplateContent> fallbackContentOpt =
                    contentRepository.findByTemplate_IdAndLanguageCode(template.getId(), defaultLangCode);

            if (fallbackContentOpt.isEmpty()) {
                throw new TemplateContentNotFoundException(templateCode, normalizedLanguage, defaultLangCode);
            }

            content = fallbackContentOpt.get();
            fallbackApplied = true;
            finalLanguage = defaultLangCode;
            log.info("Şablon varsayılan dile fallback yapıldı → templateCode={}, istenen={}, varsayılan={}",
                    templateCode, normalizedLanguage, defaultLangCode);
        }

        // 2. Depo/Lokasyon bazlı format kurallarını çözümle (locationId)
        Long locationId = null;
        if (variables != null && variables.containsKey("locationId")) {
            Object val = variables.get("locationId");
            if (val instanceof Number num) {
                locationId = num.longValue();
            } else if (val instanceof String str) {
                try {
                    locationId = Long.parseLong(str);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        if (locationId == null) {
            locationId = TenantContextHolder.getContext()
                    .map(TenantContext::locationId)
                    .orElse(null);
        }

        ActiveFormatResponse formatConfig = formatConfigService.resolveActiveFormat(locationId);

        // 3. Çözümleme stratejisini belirle
        UnresolvedPlaceholderStrategy resolvedStrategy = strategy != null ? strategy : defaultStrategy;

        // 4. Konu ve Gövde placeholder'larını çöz
        List<String> allUnresolved = new ArrayList<>();

        String resolvedSubject = null;
        if (content.getSubject() != null) {
            TemplatePlaceholderResolver.ResolvedText subjectRes =
                    placeholderResolver.resolve(content.getSubject(), variables, formatConfig, resolvedStrategy);
            resolvedSubject = subjectRes.text();
            allUnresolved.addAll(subjectRes.unresolvedPlaceholders());
        }

        TemplatePlaceholderResolver.ResolvedText bodyRes =
                placeholderResolver.resolve(content.getBody(), variables, formatConfig, resolvedStrategy);
        String resolvedBody = bodyRes.text();
        allUnresolved.addAll(bodyRes.unresolvedPlaceholders());

        return new RenderedTemplateDto(
                resolvedSubject,
                resolvedBody,
                finalLanguage,
                fallbackApplied,
                allUnresolved
        );
    }

    private void publishMissingTemplateEvent(String locale, String templateCode, String channel) {
        try {
            eventPublisher.publishEvent(new MissingTemplateEvent(this, locale, templateCode, channel));
        } catch (Exception e) {
            log.error("MissingTemplateEvent yayınlanamadı → {}", e.getMessage());
        }
    }
}
