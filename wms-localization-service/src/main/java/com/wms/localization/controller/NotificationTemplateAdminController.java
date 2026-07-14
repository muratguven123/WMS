package com.wms.localization.controller;

import com.wms.localization.dto.ImportResultDto;
import com.wms.localization.dto.notification.*;
import com.wms.localization.service.notification.NotificationTemplateAdminService;
import com.wms.localization.service.notification.NotificationTemplateCoverageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Bildirim şablonları yönetim API'si.
 * CRUD, dile özgü içerik upsert, önizleme, kapsama raporu ve import/export sağlar.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notification-templates")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('LOCALIZATION_ADMIN', 'WMS_ADMIN')")
public class NotificationTemplateAdminController {

    private final NotificationTemplateAdminService adminService;
    private final NotificationTemplateCoverageService coverageService;

    @GetMapping
    public ResponseEntity<List<NotificationTemplateDto>> listTemplates() {
        return ResponseEntity.ok(adminService.listTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationTemplateDetailDto> getTemplateDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getTemplateDetail(id));
    }

    @PostMapping
    public ResponseEntity<NotificationTemplateDto> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTemplate(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationTemplateDto> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTemplateRequest request) {
        return ResponseEntity.ok(adminService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        adminService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/contents/{languageCode}")
    public ResponseEntity<NotificationTemplateContentDto> upsertContent(
            @PathVariable Long id,
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z]{2,10}$", message = "Dil kodu sadece harflerden oluşmalı ve 2-10 karakter uzunluğunda olmalıdır")
            String languageCode,
            @Valid @RequestBody UpsertTemplateContentRequest request) {
        return ResponseEntity.ok(adminService.upsertContent(id, languageCode, request));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<RenderedTemplateDto> previewRender(
            @PathVariable Long id,
            @Valid @RequestBody PreviewRenderRequest request) {
        return ResponseEntity.ok(adminService.previewRender(id, request));
    }

    @GetMapping("/coverage")
    public ResponseEntity<TemplateCoverageReportDto> getCoverageReport() {
        return ResponseEntity.ok(coverageService.calculateCoverage());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTemplates(
            @RequestParam
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z]{2,10}$")
            String lang,

            @RequestParam(defaultValue = "xlsx")
            @Pattern(regexp = "^(xlsx|csv)$", message = "format xlsx veya csv olmalıdır")
            String format) throws IOException {

        log.info("Notification template export requested → lang={}, format={}", lang, format);

        byte[] content;
        String contentType;
        String filename;
        String today = LocalDate.now().toString();

        if ("csv".equalsIgnoreCase(format)) {
            content = adminService.exportAsCsv(lang);
            contentType = "text/csv; charset=UTF-8";
            filename = "notification_templates_" + lang + "_" + today + ".csv";
        } else {
            content = adminService.exportAsExcel(lang);
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            filename = "notification_templates_" + lang + "_" + today + ".xlsx";
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

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDto> importTemplates(
            @RequestParam
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z]{2,10}$")
            String lang,

            @RequestPart("file")
            MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Notification template import requested → lang={}, file={}, size={} bytes",
                lang, file.getOriginalFilename(), file.getSize());

        ImportResultDto result = adminService.importTemplates(lang, file);

        if (!result.getErrors().isEmpty()) {
            return ResponseEntity.status(207).body(result);
        }

        return ResponseEntity.ok(result);
    }
}
