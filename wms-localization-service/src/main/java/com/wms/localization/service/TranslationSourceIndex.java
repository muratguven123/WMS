package com.wms.localization.service;

import com.wms.localization.dto.SourceResolution;
import com.wms.localization.entity.TranslationValue;

import java.util.*;

/**
 * Modül için önceden yüklenmiş çok dilli çeviri indeksi.
 * Çeviri işi ve runtime fallback aynı sıralama mantığını paylaşır.
 */
public final class TranslationSourceIndex {

    private final String module;
    private final long expectedKeys;
    private final Map<String, Map<String, String>> valuesByLang;
    private final Map<String, Double> coverageByLang;
    private final List<String> rankedLangs;
    private final String defaultLangCode;

    public TranslationSourceIndex(
            String module,
            long expectedKeys,
            Map<String, Map<String, String>> valuesByLang,
            Map<String, Double> coverageByLang,
            List<String> rankedLangs,
            String defaultLangCode) {
        this.module = module;
        this.expectedKeys = expectedKeys;
        this.valuesByLang = valuesByLang;
        this.coverageByLang = coverageByLang;
        this.rankedLangs = rankedLangs;
        this.defaultLangCode = defaultLangCode;
    }

    public static TranslationSourceIndex build(
            String module,
            long expectedKeys,
            List<TranslationValue> allValues,
            Map<String, Double> coverageByLang,
            List<String> rankedLangs,
            String defaultLangCode) {
        Map<String, Map<String, String>> byLang = new LinkedHashMap<>();
        for (TranslationValue tv : allValues) {
            String lang = tv.getLanguage().getCode().toLowerCase(Locale.ROOT);
            String key = tv.getTranslationKey().getKeyCode();
            String value = tv.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            byLang.computeIfAbsent(lang, k -> new LinkedHashMap<>()).put(key, value);
        }
        return new TranslationSourceIndex(module, expectedKeys, byLang, coverageByLang, rankedLangs, defaultLangCode);
    }

    public String module() {
        return module;
    }

    public long expectedKeys() {
        return expectedKeys;
    }

    public Map<String, Double> coverageByLang() {
        return coverageByLang;
    }

    public List<String> getFallbackChain(String targetLang) {
        String target = targetLang.toLowerCase(Locale.ROOT);
        return rankedLangs.stream()
                .filter(code -> !code.equals(target))
                .toList();
    }

    public Optional<SourceResolution> resolveSourceForKey(
            String keyCode,
            String targetLang,
            String sourceOverride) {
        String target = targetLang.toLowerCase(Locale.ROOT);

        if (sourceOverride != null && !sourceOverride.isBlank()) {
            String override = sourceOverride.toLowerCase(Locale.ROOT);
            if (!override.equals(target)) {
                String text = valueFor(override, keyCode);
                if (text != null) {
                    return Optional.of(new SourceResolution(override, text));
                }
            }
        }

        List<String> candidates = new ArrayList<>();
        if (sourceOverride != null && !sourceOverride.isBlank()) {
            candidates.add(sourceOverride.toLowerCase(Locale.ROOT));
        }
        candidates.addAll(getFallbackChain(target));

        for (String lang : candidates) {
            if (lang.equals(target)) {
                continue;
            }
            String text = valueFor(lang, keyCode);
            if (text != null) {
                return Optional.of(new SourceResolution(lang, text));
            }
        }
        return Optional.empty();
    }

    public Map<String, String> mergeForLocale(String targetLang, Set<String> allKeyCodes) {
        String target = targetLang.toLowerCase(Locale.ROOT);
        Map<String, String> result = new LinkedHashMap<>();

        Map<String, String> targetMap = valuesByLang.getOrDefault(target, Map.of());
        List<String> chain = getFallbackChain(target);

        for (String keyCode : allKeyCodes) {
            String value = targetMap.get(keyCode);
            if (value != null && !value.isBlank()) {
                result.put(keyCode, value);
                continue;
            }
            for (String fallback : chain) {
                String fallbackValue = valueFor(fallback, keyCode);
                if (fallbackValue != null) {
                    result.put(keyCode, fallbackValue);
                    break;
                }
            }
        }
        return result;
    }

    public List<String> pivotCandidates(String sourceLang, String targetLang) {
        String source = sourceLang.toLowerCase(Locale.ROOT);
        String target = targetLang.toLowerCase(Locale.ROOT);
        return getFallbackChain(target).stream()
                .filter(code -> !code.equals(source))
                .toList();
    }

    private String valueFor(String lang, String keyCode) {
        Map<String, String> map = valuesByLang.get(lang.toLowerCase(Locale.ROOT));
        if (map == null) {
            return null;
        }
        String value = map.get(keyCode);
        return (value == null || value.isBlank()) ? null : value;
    }
}
