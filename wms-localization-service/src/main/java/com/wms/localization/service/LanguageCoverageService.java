package com.wms.localization.service;

import com.wms.localization.entity.Language;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aktif dillerin modül bazlı çeviri kapsama oranını hesaplar.
 */
@Service
@RequiredArgsConstructor
public class LanguageCoverageService {

    private final LanguageRepository languageRepository;
    private final TranslationKeyRepository translationKeyRepository;
    private final TranslationValueRepository translationValueRepository;

    public long expectedKeyCount(String module) {
        return translationKeyRepository.countByModule(module);
    }

    public Map<String, Double> coverageByLanguage(String module) {
        long expected = expectedKeyCount(module);
        if (expected == 0) {
            return Map.of();
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (Language lang : languageRepository.findAllByIsActiveTrue()) {
            String code = lang.getCode().toLowerCase(Locale.ROOT);
            long count = translationValueRepository.countByLanguageCodeAndModule(code, module);
            result.put(code, (double) count / expected);
        }
        return result;
    }

    /**
     * Hedef dil hariç, kapsama oranına göre sıralı dil listesi.
     * Tie-break: en → varsayılan dil → alfabetik.
     */
    public List<String> rankedLanguages(String module, String excludeLang) {
        String exclude = excludeLang.toLowerCase(Locale.ROOT);
        Map<String, Double> coverage = coverageByLanguage(module);
        String defaultCode = languageRepository
                .findFirstByIsDefaultTrueAndIsActiveTrue()
                .map(l -> l.getCode().toLowerCase(Locale.ROOT))
                .orElse("tr");

        return coverage.entrySet().stream()
                .filter(e -> !e.getKey().equals(exclude))
                .sorted((a, b) -> {
                    int byCoverage = Double.compare(b.getValue(), a.getValue());
                    if (byCoverage != 0) {
                        return byCoverage;
                    }
                    return tieBreak(a.getKey(), b.getKey(), defaultCode);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<String> rankedAllLanguages(String module) {
        return rankedLanguages(module, "\u0000");
    }

    private static int tieBreak(String a, String b, String defaultCode) {
        if ("en".equals(a)) return -1;
        if ("en".equals(b)) return 1;
        if (a.equals(defaultCode)) return -1;
        if (b.equals(defaultCode)) return 1;
        return a.compareTo(b);
    }
}
