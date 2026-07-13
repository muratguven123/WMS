package com.wms.localization.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.localization.dto.ImportResultDto;
import com.wms.localization.service.TranslationExportService;
import com.wms.localization.service.TranslationImportService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Çeviri Import/Export REST Controller.
 *
 * Export:
 *   GET /api/v1/translations/export?lang=de&format=xlsx
 *   GET /api/v1/translations/export?lang=de&format=json
 *
 * Import:
 *   POST /api/v1/translations/import?lang=de
 *   Content-Type: multipart/form-data
 *   file: translations_de.xlsx veya translations_de.json
 *
 * Yetkilendirme: Her iki endpoint de ADMIN rolü gerektirir.
 *   (SecurityConfig'de `hasRole("ADMIN")` ile koruma altına alınmalıdır)
 *
 * Dosya adı şeması: translations_{lang}_{tarih}.xlsx
 * Örnek: translations_de_2026-07-03.xlsx
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/translations")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('LOCALIZATION_ADMIN', 'WMS_ADMIN')")
public class TranslationImportExportController {

    private final TranslationExportService exportService;
    private final TranslationImportService importService;

    // -------------------------------------------------------------------------
    // EXPORT — GET /api/v1/translations/export
    // -------------------------------------------------------------------------

    /**
     * @param lang   ISO 639-1 dil kodu (örn: de, tr, en)
     * @param format Çıktı formatı: xlsx (default) | json
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTranslations(
            @RequestParam
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z]{2,10}$")
            String lang,

            @RequestParam(defaultValue = "xlsx")
            @Pattern(regexp = "^(xlsx|json)$", message = "format xlsx veya json olmalıdır")
            String format) throws IOException {

        log.info("Export isteği → lang={}, format={}", lang, format);

        byte[] content;
        String contentType;
        String filename;
        String today = LocalDate.now().toString();

        if ("json".equalsIgnoreCase(format)) {
            content     = exportService.exportAsJson(lang);
            contentType = MediaType.APPLICATION_JSON_VALUE;
            filename    = "translations_" + lang + "_" + today + ".json";
        } else {
            content     = exportService.exportAsExcel(lang);
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            filename    = "translations_" + lang + "_" + today + ".xlsx";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    // -------------------------------------------------------------------------
    // IMPORT — POST /api/v1/translations/import
    // -------------------------------------------------------------------------

    /**
     * @param lang ISO 639-1 dil kodu
     * @param file .xlsx veya .json dosyası (multipart/form-data)
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> importTranslations(
            @RequestParam
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z]{2,10}$")
            String lang,

            @RequestPart("file")
            MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Import isteği → lang={}, dosya={}, boyut={} byte",
                lang, file.getOriginalFilename(), file.getSize());

        ImportResultDto result = importService.importTranslations(lang, file);

        // Hata varsa 207 Multi-Status — kısmi başarı
        if (!result.getErrors().isEmpty()) {
            return ResponseEntity.status(207).body(result);
        }

        return ResponseEntity.ok(result);
    }
}
