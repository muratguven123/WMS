package com.wms.localization.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class LibreTranslateProvider implements TranslationProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wms.i18n.libretranslate-url:https://libretranslate.com/translate}")
    private String libreTranslateUrl;

    @Override
    public String name() {
        return "libretranslate";
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> payload = Map.of(
                    "q", text,
                    "source", sourceLang,
                    "target", targetLang.toLowerCase(),
                    "format", "text");

            String response = restTemplate.postForObject(
                    libreTranslateUrl,
                    new HttpEntity<>(payload, headers),
                    String.class);

            if (response == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            String translated = root.path("translatedText").asText("");
            if (translated.isBlank() || translated.equalsIgnoreCase(text)) {
                return null;
            }
            return translated;
        } catch (Exception ex) {
            log.debug("LibreTranslate hatası: {}", ex.getMessage());
            return null;
        }
    }
}
