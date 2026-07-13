package com.wms.localization.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class MyMemoryTranslationProvider implements TranslationProvider {

    private static final String URL = "https://api.mymemory.translated.net/get";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wms.i18n.mymemory-email:}")
    private String myMemoryEmail;

    @Override
    public String name() {
        return "mymemory";
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            String langPair = sourceLang + "|" + targetLang.toLowerCase();
            StringBuilder url = new StringBuilder(URL)
                    .append("?q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8))
                    .append("&langpair=").append(URLEncoder.encode(langPair, StandardCharsets.UTF_8));
            if (!myMemoryEmail.isBlank()) {
                url.append("&de=").append(URLEncoder.encode(myMemoryEmail, StandardCharsets.UTF_8));
            }

            String body = restTemplate.getForObject(url.toString(), String.class);
            if (body == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(body);
            String status = root.path("responseStatus").asText("");
            if ("403".equals(status) || "429".equals(status)) {
                return null;
            }

            String translated = root.path("responseData").path("translatedText").asText("");
            if (translated.isBlank()
                    || translated.toUpperCase().contains("MYMEMORY WARNING")
                    || translated.equalsIgnoreCase(text)) {
                return null;
            }
            return translated;
        } catch (Exception ex) {
            log.debug("MyMemory hatası: {}", ex.getMessage());
            return null;
        }
    }
}
