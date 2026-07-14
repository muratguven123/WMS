package com.wms.localization.service.notification;

import com.wms.localization.dto.notification.TemplateCoverageReportDto;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.NotificationTemplate;
import com.wms.localization.entity.NotificationTemplateContent;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.NotificationTemplateContentRepository;
import com.wms.localization.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Bildirim şablonlarının aktif dillerdeki çeviri durumlarını ve kapsama oranlarını hesaplayan servis.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationTemplateCoverageService {

    private final LanguageRepository languageRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateContentRepository contentRepository;

    /**
     * Kapsama raporu hazırlar.
     */
    public TemplateCoverageReportDto calculateCoverage() {
        List<Language> activeLanguages = languageRepository.findAllByIsActiveTrue();
        List<NotificationTemplate> activeTemplates = templateRepository.findAllByActiveTrueOrderByTemplateCodeAsc();
        List<NotificationTemplateContent> allContents = contentRepository.findAllWithTemplate();

        // Her dil için hangi şablonların içeriği var haritası: languageCode -> Set<templateCode>
        Map<String, Set<String>> languageToTemplates = new HashMap<>();
        for (Language lang : activeLanguages) {
            languageToTemplates.put(lang.getCode().toLowerCase(Locale.ROOT), new HashSet<>());
        }

        for (NotificationTemplateContent content : allContents) {
            String lang = content.getLanguageCode().toLowerCase(Locale.ROOT);
            if (languageToTemplates.containsKey(lang)) {
                languageToTemplates.get(lang).add(content.getTemplate().getTemplateCode());
            }
        }

        // Kapsama oranlarını hesapla: languageCode -> ratio
        long totalTemplatesCount = activeTemplates.size();
        Map<String, Double> coverageRatio = new LinkedHashMap<>();
        for (Language lang : activeLanguages) {
            String code = lang.getCode().toLowerCase(Locale.ROOT);
            if (totalTemplatesCount == 0) {
                coverageRatio.put(code, 0.0);
            } else {
                long translationCount = languageToTemplates.get(code).size();
                coverageRatio.put(code, (double) translationCount / totalTemplatesCount);
            }
        }

        // Her şablon için hangi aktif dillerin eksik olduğunu bul: templateCode -> List<languageCode>
        Map<String, List<String>> missingLanguagesPerTemplate = new LinkedHashMap<>();
        for (NotificationTemplate template : activeTemplates) {
            String templateCode = template.getTemplateCode();
            List<String> missingLangs = new ArrayList<>();
            for (Language lang : activeLanguages) {
                String langCode = lang.getCode().toLowerCase(Locale.ROOT);
                if (!languageToTemplates.get(langCode).contains(templateCode)) {
                    missingLangs.add(langCode);
                }
            }
            if (!missingLangs.isEmpty()) {
                missingLanguagesPerTemplate.put(templateCode, missingLangs);
            }
        }

        return TemplateCoverageReportDto.builder()
                .coverageRatio(coverageRatio)
                .missingLanguagesPerTemplate(missingLanguagesPerTemplate)
                .build();
    }
}
