package com.wms.localization.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.TranslationKey;
import com.wms.localization.entity.TranslationValue;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Uygulama başlangıcında frontend çeviri anahtarlarını DB'ye senkronize eder.
 * TR ve EN değerleri ui-translations.json dosyasından okunur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationBootstrapService implements ApplicationRunner {

    private static final String RESOURCE = "i18n/ui-translations.json";

    private final TranslationKeyRepository translationKeyRepository;
    private final TranslationValueRepository translationValueRepository;
    private final LanguageRepository languageRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int synced = syncFromClasspath();
            if (synced > 0) {
                log.info("UI çeviri bootstrap tamamlandı → {} anahtar senkronize edildi", synced);
            }
        } catch (Exception ex) {
            log.error("UI çeviri bootstrap başarısız: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public int syncFromClasspath() throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.warn("Bootstrap dosyası bulunamadı: {}", RESOURCE);
            return 0;
        }

        JsonNode root;
        try (InputStream in = resource.getInputStream()) {
            root = objectMapper.readTree(in);
        }

        JsonNode keysNode = root.path("keys");
        if (!keysNode.isObject()) {
            return 0;
        }

        Optional<Language> trLang = languageRepository.findByCode("tr");
        Optional<Language> enLang = languageRepository.findByCode("en");
        if (trLang.isEmpty() || enLang.isEmpty()) {
            log.warn("TR/EN dilleri bulunamadı — bootstrap atlanıyor");
            return 0;
        }

        int count = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = keysNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String keyCode = field.getKey();
            JsonNode values = field.getValue();

            TranslationKey key = translationKeyRepository.findByKeyCode(keyCode)
                    .orElseGet(() -> translationKeyRepository.save(
                            TranslationKey.builder()
                                    .keyCode(keyCode)
                                    .module("UI")
                                    .description("UI bootstrap")
                                    .build()));

            upsertValue(trLang.get(), key, values.path("tr").asText(""));
            upsertValue(enLang.get(), key, values.path("en").asText(""));
            count++;
        }
        return count;
    }

    private void upsertValue(Language language, TranslationKey key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
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
