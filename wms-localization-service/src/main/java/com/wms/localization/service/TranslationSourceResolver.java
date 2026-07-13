package com.wms.localization.service;

import com.wms.localization.entity.Language;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Her anahtar için en dolu kaynak dili seçer; runtime fallback zincirini üretir.
 */
@Service
@RequiredArgsConstructor
public class TranslationSourceResolver {

    private final TranslationValueRepository translationValueRepository;
    private final TranslationKeyRepository translationKeyRepository;
    private final LanguageRepository languageRepository;
    private final LanguageCoverageService coverageService;

    public TranslationSourceIndex buildIndex(String module) {
        long expected = translationKeyRepository.countByModule(module);
        List<com.wms.localization.entity.TranslationValue> values =
                translationValueRepository.findAllNonBlankByModule(module);
        Map<String, Double> coverage = coverageService.coverageByLanguage(module);
        List<String> ranked = coverageService.rankedAllLanguages(module);
        String defaultCode = languageRepository
                .findFirstByIsDefaultTrueAndIsActiveTrue()
                .map(Language::getCode)
                .map(c -> c.toLowerCase(Locale.ROOT))
                .orElse("tr");

        return TranslationSourceIndex.build(module, expected, values, coverage, ranked, defaultCode);
    }

    public Set<String> allKeyCodes(String module) {
        return translationKeyRepository.findAllByModule(module).stream()
                .map(k -> k.getKeyCode())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
