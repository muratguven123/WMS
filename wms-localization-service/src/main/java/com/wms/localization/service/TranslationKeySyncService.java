package com.wms.localization.service;

import com.wms.localization.dto.SyncTranslationKeyRequest;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.TranslationKey;
import com.wms.localization.entity.TranslationValue;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationKeySyncService {

    private final TranslationKeyRepository translationKeyRepository;
    private final TranslationValueRepository translationValueRepository;
    private final LanguageRepository languageRepository;
    private final LanguageAutoTranslateService autoTranslateService;
    private final TranslationService translationService;

    @Transactional
    public int syncKeys(List<SyncTranslationKeyRequest> requests) {
        Optional<Language> trLang = languageRepository.findByCode("tr");
        Optional<Language> enLang = languageRepository.findByCode("en");
        int count = 0;

        for (SyncTranslationKeyRequest req : requests) {
            TranslationKey key = translationKeyRepository.findByKeyCode(req.keyCode())
                    .orElseGet(() -> translationKeyRepository.save(
                            TranslationKey.builder()
                                    .keyCode(req.keyCode())
                                    .module(req.module() != null ? req.module() : "UI")
                                    .description(req.description() != null ? req.description() : "Synced key")
                                    .build()));

            if (trLang.isPresent() && req.tr() != null && !req.tr().isBlank()) {
                upsert(trLang.get(), key, req.tr());
            }
            if (enLang.isPresent() && req.en() != null && !req.en().isBlank()) {
                upsert(enLang.get(), key, req.en());
            }
            count++;
        }

        translationService.evictAllCache();
        log.info("Çeviri anahtarları senkronize edildi → {} anahtar", count);

        languageRepository.findAllByIsActiveTrue().stream()
                .filter(l -> !l.isDefault() && !"en".equalsIgnoreCase(l.getCode()))
                .forEach(l -> autoTranslateService.autoTranslate(l.getCode(), false));

        return count;
    }

    private void upsert(Language language, TranslationKey key, String value) {
        translationValueRepository
                .findByLanguageCodeAndKeyCode(language.getCode(), key.getKeyCode())
                .ifPresentOrElse(
                        existing -> {
                            if (!value.equals(existing.getValue())) {
                                existing.setValue(value);
                                translationValueRepository.save(existing);
                            }
                        },
                        () -> translationValueRepository.save(
                                TranslationValue.builder()
                                        .language(language)
                                        .translationKey(key)
                                        .value(value)
                                        .build()));
    }
}
