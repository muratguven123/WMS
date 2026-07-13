package com.wms.localization.service;

import com.wms.localization.config.RedisConfig;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.TranslationKey;
import com.wms.localization.entity.TranslationValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private TranslationSourceResolver sourceResolver;

    @InjectMocks
    private TranslationServiceImpl translationService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getTranslations_returnsCachedValueOnHit() {
        Map<String, String> cached = Map.of("common.buttons.save", "Kaydet");
        when(valueOps.get("translations:de:UI")).thenReturn(cached);

        Map<String, String> result = translationService.getTranslations("de", "UI");

        assertThat(result).isEqualTo(cached);
        verify(sourceResolver, never()).buildIndex(any());
    }

    @Test
    void getTranslations_usesSequentialFallbackOnCacheMiss() {
        when(valueOps.get("translations:de:UI")).thenReturn(null);

        TranslationValue deSave = tv("de", "common.buttons.save", "Speichern");
        TranslationValue trSave = tv("tr", "common.buttons.save", "Kaydet");
        TranslationValue trCancel = tv("tr", "common.buttons.cancel", "İptal");

        TranslationSourceIndex index = TranslationSourceIndex.build(
                "UI",
                2,
                List.of(deSave, trSave, trCancel),
                Map.of("tr", 1.0, "de", 0.5),
                List.of("tr", "de"),
                "tr");

        when(sourceResolver.buildIndex("UI")).thenReturn(index);
        when(sourceResolver.allKeyCodes("UI"))
                .thenReturn(Set.of("common.buttons.save", "common.buttons.cancel"));

        Map<String, String> result = translationService.getTranslations("de", "UI");

        assertThat(result)
                .containsEntry("common.buttons.save", "Speichern")
                .containsEntry("common.buttons.cancel", "İptal");

        verify(valueOps).set(
                eq("translations:de:UI"),
                eq(result),
                eq(RedisConfig.TTL_TRANSLATIONS.toSeconds()),
                eq(TimeUnit.SECONDS));
    }

    private static TranslationValue tv(String langCode, String keyCode, String value) {
        Language lang = Language.builder().code(langCode).build();
        TranslationKey key = TranslationKey.builder().keyCode(keyCode).module("UI").build();
        return TranslationValue.builder().language(lang).translationKey(key).value(value).build();
    }
}
