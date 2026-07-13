package com.wms.localization.util;

import java.util.Locale;
import java.util.Map;

/**
 * ISO 639-1 dil kodu doğrulama ve yaygın yazım hatalarını düzeltme.
 */
public final class LanguageCodeNormalizer {

    private static final Map<String, String> NAME_ALIASES = Map.ofEntries(
            Map.entry("russian", "ru"),
            Map.entry("rusca", "ru"),
            Map.entry("rusça", "ru"),
            Map.entry("english", "en"),
            Map.entry("ingilizce", "en"),
            Map.entry("turkish", "tr"),
            Map.entry("turkce", "tr"),
            Map.entry("türkçe", "tr"),
            Map.entry("german", "de"),
            Map.entry("deutsch", "de"),
            Map.entry("almanca", "de"),
            Map.entry("french", "fr"),
            Map.entry("francais", "fr"),
            Map.entry("français", "fr"),
            Map.entry("fransizca", "fr"),
            Map.entry("fransızca", "fr"),
            Map.entry("spanish", "es"),
            Map.entry("ispanyolca", "es"),
            Map.entry("italian", "it"),
            Map.entry("italyanca", "it"),
            Map.entry("arabic", "ar"),
            Map.entry("arapca", "ar"),
            Map.entry("arapça", "ar"),
            Map.entry("chinese", "zh"),
            Map.entry("cince", "zh"),
            Map.entry("çince", "zh"),
            Map.entry("japanese", "ja"),
            Map.entry("japonca", "ja"),
            Map.entry("korean", "ko"),
            Map.entry("korece", "ko"),
            Map.entry("portuguese", "pt"),
            Map.entry("portekizce", "pt"),
            Map.entry("dutch", "nl"),
            Map.entry("hollandaca", "nl"),
            Map.entry("polish", "pl"),
            Map.entry("lehce", "pl"),
            Map.entry("lehçe", "pl"),
            Map.entry("serbian", "rs"),
            Map.entry("sirpca", "rs"),
            Map.entry("sırpça", "rs")
    );

    private LanguageCodeNormalizer() {
    }

    public static String normalize(String rawCode, String name) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Dil kodu zorunludur");
        }

        String code = rawCode.trim().toLowerCase(Locale.ROOT);

        if (code.length() > 2) {
            String alias = NAME_ALIASES.get(code);
            if (alias != null) {
                code = alias;
            } else {
                throw new IllegalArgumentException(
                        "Dil kodu ISO 639-1 olmalıdır (2 harf, örn. ru, de, fr). Girilen: " + rawCode);
            }
        }

        if (code.length() != 2 || !code.matches("[a-z]{2}")) {
            throw new IllegalArgumentException(
                    "Dil kodu tam 2 harf olmalıdır (örn. ru). Girilen: " + rawCode);
        }

        validateNameCodePair(code, name);
        return code;
    }

    private static void validateNameCodePair(String code, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String lowerName = name.trim().toLowerCase(Locale.ROOT);
        boolean russianName = lowerName.contains("russian")
                || lowerName.contains("rusça")
                || lowerName.contains("rusca");
        if (russianName && !"ru".equals(code)) {
            throw new IllegalArgumentException(
                    "Rusça için dil kodu 'ru' olmalıdır. 'rs' Sırpça (Sırbistan) kodudur.");
        }
    }
}
