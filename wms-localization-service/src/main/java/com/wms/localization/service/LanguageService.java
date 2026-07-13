package com.wms.localization.service;

import com.wms.localization.entity.Language;
import com.wms.localization.event.LanguageCreatedEvent;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.MissingTranslationLogRepository;
import com.wms.localization.repository.TranslationValueRepository;
import com.wms.localization.util.LanguageCodeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dil yönetim servisi.
 *
 * addLanguage() akışı:
 *  1. Kod benzersizliğini doğrula.
 *  2. Yeni dili DB'ye kaydet.
 *  3. LanguageCreatedEvent yayınla → LanguageEventListener async devralır.
 *
 * NOT: Event @Async ile dinlendiği için, bu metodun transaction'ı
 * commit olduktan SONRA listener devreye girer (TransactionSynchronization
 * kullanarak event yayımı transaction commit sonrasına ertelenir).
 * Bu sayede listener'ın DB okumalarında yeni dil kaydı görünür olur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageService {

    private final LanguageRepository       languageRepository;
    private final TranslationValueRepository translationValueRepository;
    private final MissingTranslationLogRepository missingTranslationLogRepository;
    private final TranslationService translationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Language addLanguage(String code, String name) {
        String normalizedCode = LanguageCodeNormalizer.normalize(code, name);

        // Benzersizlik kontrolü
        if (languageRepository.findByCode(normalizedCode).isPresent()) {
            throw new IllegalArgumentException("Bu dil kodu zaten mevcut: " + normalizedCode);
        }

        Language language = Language.builder()
                .code(normalizedCode)
                .name(name.trim())
                .isDefault(false)
                .isActive(true)
                .build();

        Language saved = languageRepository.save(language);
        log.info("Yeni dil eklendi → code={}, id={}", saved.getCode(), saved.getId());

        // Transaction commit sonrası listener'ı tetikle
        // Spring'in TransactionSynchronizationManager'ı afterCommit hook'u sağlar
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                eventPublisher.publishEvent(
                                        new LanguageCreatedEvent(LanguageService.this, saved));
                            }
                        }
                );

        return saved;
    }

    /**
     * Dili pasifleştirir ve çeviri verilerini temizler.
     * Varsayılan dil veya zaten pasif dil için hata fırlatır.
     */
    @Transactional
    public Language deactivateLanguage(String code) {
        String normalizedCode = LanguageCodeNormalizer.normalize(code, null);

        Language language = languageRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("Dil bulunamadı: " + normalizedCode));

        if (language.isDefault()) {
            throw new IllegalArgumentException("Varsayılan dil pasifleştirilemez: " + normalizedCode);
        }
        if (!language.isActive()) {
            throw new IllegalArgumentException("Dil zaten pasif: " + normalizedCode);
        }

        translationValueRepository.deleteAllByLanguageId(language.getId());
        missingTranslationLogRepository.deleteAllByLocale(normalizedCode);

        language.setActive(false);
        Language saved = languageRepository.save(language);

        for (String module : List.of("UI", "REPORT", "EMAIL", "SYSTEM")) {
            translationService.evictCache(normalizedCode, module);
        }

        log.info("Dil pasifleştirildi → code={}, id={}", saved.getCode(), saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Language> getActiveLanguages() {
        return languageRepository.findAllByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Language> getAllLanguages() {
        return languageRepository.findAll();
    }
}
