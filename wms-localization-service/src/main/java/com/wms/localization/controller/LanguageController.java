package com.wms.localization.controller;

import com.wms.localization.dto.AddLanguageRequest;
import com.wms.localization.entity.Language;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import com.wms.localization.service.LanguageAutoTranslateService;
import com.wms.localization.service.LanguageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Dil yönetimi REST API.
 *
 * Yeni dil eklendiğinde {@link com.wms.localization.event.LanguageEventListener}
 * varsayılan dildeki çevirileri otomatik kopyalar.
 */
@RestController
@RequestMapping("/api/v1/languages")
@RequiredArgsConstructor
public class LanguageController {

    private final LanguageService languageService;
    private final LanguageAutoTranslateService autoTranslateService;
    private final TranslationValueRepository translationValueRepository;
    private final TranslationKeyRepository translationKeyRepository;

    @GetMapping
    public ResponseEntity<List<Language>> getLanguages(
            @RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(all
                ? languageService.getAllLanguages()
                : languageService.getActiveLanguages());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<Language> addLanguage(@Valid @RequestBody AddLanguageRequest request) {
        Language language = languageService.addLanguage(request.code(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(language);
    }

    /** Dili pasifleştirir; dil menüsünden kaldırır, çeviri verilerini temizler. */
    @DeleteMapping("/{code}")
    @PreAuthorize("hasAnyRole('LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<Language> deactivateLanguage(@PathVariable String code) {
        return ResponseEntity.ok(languageService.deactivateLanguage(code));
    }

    /** Mevcut dil için otomatik çeviriyi tetikler (eksik anahtarlar veya force=true ile tümü). */
    @PostMapping("/{code}/auto-translate")
    @PreAuthorize("hasAnyRole('LOCALIZATION_ADMIN', 'WMS_ADMIN')")
    public ResponseEntity<Map<String, Object>> autoTranslate(
            @PathVariable String code,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(required = false) String source) {
        int count = autoTranslateService.autoTranslate(code, force, source);
        return ResponseEntity.ok(Map.of(
                "language", code,
                "translatedKeys", count,
                "async", count < 0,
                "source", source != null ? source : "auto"));
    }

    /** UI çeviri ilerlemesi — frontend polling için. */
    @GetMapping("/{code}/translation-status")
    public ResponseEntity<Map<String, Object>> translationStatus(@PathVariable String code) {
        String lang = code.toLowerCase();
        long uiCount = translationValueRepository.countByLanguageCodeAndModule(lang, "UI");
        long expected = translationKeyRepository.countByModule("UI");
        long missingKeys = translationValueRepository.countMissingTranslationsByLanguageAndModule(lang, "UI");
        boolean ready = expected > 0 && missingKeys == 0;
        return ResponseEntity.ok(Map.of(
                "language", lang,
                "uiKeyCount", uiCount,
                "expectedKeys", expected,
                "missingKeys", missingKeys,
                "untranslatedCount", missingKeys,
                "ready", ready));
    }
}
