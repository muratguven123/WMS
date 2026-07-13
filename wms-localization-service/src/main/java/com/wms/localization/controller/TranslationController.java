package com.wms.localization.controller;

import com.wms.localization.service.TranslationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Çeviri paketi REST API.
 *
 * Endpoint'ler:
 *
 *   GET  /api/v1/translations
 *        Params: locale (zorunlu), module (zorunlu)
 *        Döner:  Map<String,String> — keyCode → çeviri metni
 *
 *   DELETE /api/v1/translations/cache
 *        Params: locale, module (her ikisi de opsiyonel)
 *        Her ikisi de sağlanırsa → tek cache key temizlenir.
 *        Hiçbiri sağlanmazsa    → tüm çeviri cache'i temizlenir.
 *        (Yetkilendirme: ADMIN rolü gerektirir — SecurityConfig'de tanımlı)
 *
 * HTTP Cache-Control başlığı:
 *   GET yanıtına "max-age=3600, must-revalidate" eklenir.
 *   Browser/CDN bu süre boyunca istek atmaz → Redis yükü daha da azalır.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/translations")
@RequiredArgsConstructor
@Validated
public class TranslationController {

    private final TranslationService translationService;

    // -------------------------------------------------------------------------
    // GET /api/v1/translations?locale=de&module=UI
    // -------------------------------------------------------------------------

    /**
     * @param locale  ISO 639-1 dil kodu (2-10 karakter, sadece harf)
     * @param module  Modül adı: UI | REPORT | EMAIL | SYSTEM
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getTranslations(
            @RequestParam
            @NotBlank(message = "locale parametresi boş olamaz")
            @Size(min = 2, max = 10, message = "locale 2-10 karakter olmalıdır")
            @Pattern(regexp = "^[a-zA-Z]{2,10}$", message = "locale yalnızca harf içermelidir")
            String locale,

            @RequestParam
            @NotBlank(message = "module parametresi boş olamaz")
            @Pattern(regexp = "^(UI|REPORT|EMAIL|SYSTEM)$",
                     message = "module değeri UI, REPORT, EMAIL veya SYSTEM olmalıdır")
            String module) {

        log.debug("Çeviri paketi istendi → locale={}, module={}", locale, module);

        Map<String, String> translations = translationService.getTranslations(locale, module);

        return ResponseEntity.ok()
                // Browser & CDN 1 saat cache'lesin; sonrasında revalidate etsin
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).mustRevalidate())
                .body(translations);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/translations/cache  (Admin)
    // -------------------------------------------------------------------------

    /**
     * Belirtilen locale+module cache'ini temizler.
     * Her ikisi de verilmezse tüm çeviri cache'ini temizler.
     *
     * Örnek kullanım:
     *   DELETE /api/v1/translations/cache?locale=de&module=UI  → tek key
     *   DELETE /api/v1/translations/cache                       → tümü
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, Object>> evictCache(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String module) {

        if (locale != null && module != null) {
            translationService.evictCache(locale, module);
            log.info("Cache temizlendi → locale={}, module={}", locale, module);
            return ResponseEntity.ok(Map.of(
                    "status",  "evicted",
                    "locale",  locale,
                    "module",  module
            ));
        }

        translationService.evictAllCache();
        log.info("Tüm çeviri cache'i temizlendi");
        return ResponseEntity.ok(Map.of(
                "status", "all_evicted"
        ));
    }
}
