package com.wms.localization.service;

import com.wms.localization.entity.Language;
import com.wms.localization.entity.TranslationKey;
import com.wms.localization.entity.TranslationValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationSourceIndexTest {

    @Test
    void resolveSourceForKey_picksHighestCoverageLanguage() {
        TranslationSourceIndex index = indexWith(
                tv("en", "key.a", "Hello"),
                tv("de", "key.a", "Hallo"),
                tv("tr", "key.a", "Merhaba"));

        Optional<com.wms.localization.dto.SourceResolution> res =
                index.resolveSourceForKey("key.a", "fr", null);

        assertThat(res).isPresent();
        assertThat(res.get().sourceLang()).isEqualTo("en");
        assertThat(res.get().text()).isEqualTo("Hello");
    }

    @Test
    void resolveSourceForKey_honorsExplicitOverride() {
        TranslationSourceIndex index = indexWith(
                tv("en", "key.a", "Hello"),
                tv("de", "key.a", "Hallo"));

        Optional<com.wms.localization.dto.SourceResolution> res =
                index.resolveSourceForKey("key.a", "fr", "de");

        assertThat(res).isPresent();
        assertThat(res.get().sourceLang()).isEqualTo("de");
    }

    @Test
    void mergeForLocale_prefersTargetThenSingleFallback() {
        TranslationSourceIndex index = indexWith(
                tv("de", "key.a", "Speichern"),
                tv("en", "key.a", "Save"),
                tv("en", "key.b", "Cancel"),
                tv("tr", "key.b", "İptal"));

        Map<String, String> merged = index.mergeForLocale("de", Set.of("key.a", "key.b"));

        assertThat(merged)
                .containsEntry("key.a", "Speichern")
                .containsEntry("key.b", "Cancel");
    }

    private static TranslationSourceIndex indexWith(TranslationValue... values) {
        return TranslationSourceIndex.build(
                "UI",
                2,
                List.of(values),
                Map.of("en", 1.0, "de", 0.5, "tr", 0.5),
                List.of("en", "de", "tr"),
                "tr");
    }

    private static TranslationValue tv(String langCode, String keyCode, String value) {
        Language lang = Language.builder().code(langCode).build();
        TranslationKey key = TranslationKey.builder().keyCode(keyCode).module("UI").build();
        return TranslationValue.builder().language(lang).translationKey(key).value(value).build();
    }
}
