package com.wms.localization.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.wms.localization.service.translation.TranslationProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Otomatik makine çevirisi — pluggable {@link TranslationProvider} zinciri.
 * Herhangi bir kaynak→hedef dil çiftini destekler; direct başarısızsa pivot dener.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MachineTranslationService {

    private static final int MAX_CHARS = 480;

    private final List<TranslationProvider> providers;

    @Value("${wms.i18n.auto-translate-enabled:true}")
    private boolean autoTranslateEnabled;

    @Value("${wms.i18n.translate-delay-ms:80}")
    private long translateDelayMs;

    public boolean isEnabled() {
        return autoTranslateEnabled;
    }

    /**
     * Direct çeviri dener; başarısız veya kaynakla aynıysa null döner.
     */
    public String translateDirect(String text, String sourceLang, String targetLang) {
        if (!autoTranslateEnabled || text == null || text.isBlank()) {
            return text;
        }
        if (sourceLang.equalsIgnoreCase(targetLang)) {
            return text;
        }

        String chunk = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;

        for (TranslationProvider provider : providers) {
            String result = provider.translate(chunk, sourceLang, targetLang);
            if (result != null && !result.equalsIgnoreCase(chunk)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Direct çeviri; başarısızsa pivot diller üzerinden 2 adımlı çeviri dener.
     */
    public String translate(String text, String sourceLang, String targetLang, List<String> pivotCandidates) {
        if (!autoTranslateEnabled || text == null || text.isBlank()) {
            return text;
        }
        if (sourceLang.equalsIgnoreCase(targetLang)) {
            return text;
        }

        String direct = translateDirect(text, sourceLang, targetLang);
        if (direct != null) {
            return direct;
        }

        if (pivotCandidates == null || pivotCandidates.isEmpty()) {
            return null;
        }

        for (String pivot : pivotCandidates) {
            if (pivot.equalsIgnoreCase(sourceLang) || pivot.equalsIgnoreCase(targetLang)) {
                continue;
            }
            String intermediate = translateDirect(text, sourceLang, pivot);
            if (intermediate == null) {
                continue;
            }
            String finalResult = translateDirect(intermediate, pivot, targetLang);
            if (finalResult != null) {
                log.debug("Pivot çeviri → {}→{}→{}", sourceLang, pivot, targetLang);
                return finalResult;
            }
        }
        return null;
    }

    public Map<String, String> translateBatch(
            Map<String, com.wms.localization.dto.SourceResolution> sourcesByKey,
            String targetLang,
            TranslationSourceIndex index) {
        Map<String, String> result = new LinkedHashMap<>();
        int i = 0;
        int total = sourcesByKey.size();

        for (Map.Entry<String, com.wms.localization.dto.SourceResolution> entry : sourcesByKey.entrySet()) {
            com.wms.localization.dto.SourceResolution source = entry.getValue();
            String translated = translate(
                    source.text(),
                    source.sourceLang(),
                    targetLang,
                    index.pivotCandidates(source.sourceLang(), targetLang));
            if (translated != null) {
                result.put(entry.getKey(), translated);
            }
            i++;
            if (translateDelayMs > 0 && i < total) {
                try {
                    Thread.sleep(translateDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (i % 25 == 0) {
                log.info("Çeviri ilerlemesi → {}/{} (→{})", i, total, targetLang);
            }
        }
        return result;
    }
}
