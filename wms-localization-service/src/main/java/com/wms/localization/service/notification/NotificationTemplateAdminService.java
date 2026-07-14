package com.wms.localization.service.notification;

import com.wms.localization.dto.ImportResultDto;
import com.wms.localization.dto.notification.*;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.NotificationChannel;
import com.wms.localization.entity.NotificationTemplate;
import com.wms.localization.entity.NotificationTemplateContent;
import com.wms.localization.exception.notification.NotificationTemplateConflictException;
import com.wms.localization.exception.notification.NotificationTemplateNotFoundException;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.MissingTemplateLogRepository;
import com.wms.localization.repository.NotificationTemplateContentRepository;
import com.wms.localization.repository.NotificationTemplateRepository;
import com.wms.localization.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bildirim şablonu yönetim (Admin) işlemlerini karşılayan servis.
 * CRUD, içerik upsert, önizleme ve import/export işlemlerini yürütür.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTemplateAdminService {

    private static final String[] EXPORT_HEADERS = {
            "Template Code", "Channel", "Subject", "Body", "Description", "Active"
    };

    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateContentRepository contentRepository;
    private final LanguageRepository languageRepository;
    private final MissingTemplateLogRepository missingTemplateLogRepository;
    private final NotificationTemplateRenderService renderService;

    // =========================================================================
    // CRUD İşlemleri
    // =========================================================================

    @Transactional(readOnly = true)
    public List<NotificationTemplateDto> listTemplates() {
        return templateRepository.findAllByOrderByTemplateCodeAsc().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationTemplateDetailDto getTemplateDetail(Long id) {
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> NotificationTemplateNotFoundException.byId(id));

        List<NotificationTemplateContentDto> contents = contentRepository
                .findAllByTemplate_IdOrderByLanguageCodeAsc(id).stream()
                .map(this::mapToContentDto)
                .toList();

        return NotificationTemplateDetailDto.builder()
                .id(template.getId())
                .templateCode(template.getTemplateCode())
                .channel(template.getChannel())
                .description(template.getDescription())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .contents(contents)
                .build();
    }

    @Transactional
    public NotificationTemplateDto createTemplate(CreateTemplateRequest request) {
        if (templateRepository.existsByTemplateCode(request.getTemplateCode())) {
            throw new NotificationTemplateConflictException(
                    "Şablon kodu zaten mevcut: " + request.getTemplateCode());
        }

        String user = getAuditorUser();

        NotificationTemplate template = NotificationTemplate.builder()
                .templateCode(request.getTemplateCode())
                .channel(request.getChannel())
                .description(request.getDescription())
                .active(request.isActive())
                .createdBy(user)
                .updatedBy(user)
                .build();

        NotificationTemplate saved = templateRepository.save(template);
        log.info("Yeni bildirim şablonu oluşturuldu → id={}, code={}, channel={}",
                saved.getId(), saved.getTemplateCode(), saved.getChannel());

        return mapToDto(saved);
    }

    @Transactional
    public NotificationTemplateDto updateTemplate(Long id, UpdateTemplateRequest request) {
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> NotificationTemplateNotFoundException.byId(id));

        String user = getAuditorUser();

        template.setDescription(request.getDescription());
        template.setActive(request.isActive());
        template.setUpdatedBy(user);

        NotificationTemplate saved = templateRepository.save(template);
        log.info("Bildirim şablonu güncellendi → id={}, code={}", saved.getId(), saved.getTemplateCode());

        return mapToDto(saved);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> NotificationTemplateNotFoundException.byId(id));

        templateRepository.delete(template);
        log.info("Bildirim şablonu silindi → id={}, code={}", template.getId(), template.getTemplateCode());
    }

    // =========================================================================
    // İçerik (Dile Özgü) Yönetimi
    // =========================================================================

    @Transactional
    public NotificationTemplateContentDto upsertContent(Long templateId,
                                                        String languageCode,
                                                        UpsertTemplateContentRequest request) {
        NotificationTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> NotificationTemplateNotFoundException.byId(templateId));

        String normalizedLang = languageCode.toLowerCase(Locale.ROOT);

        // Dilin sistemde aktif olarak tanımlı olduğundan emin olalım
        languageRepository.findByCode(normalizedLang)
                .filter(Language::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dil sistemde tanımlı veya aktif değil: " + languageCode));

        // EMAIL şablonlarında subject zorunludur
        if (template.getChannel() == NotificationChannel.EMAIL &&
                (request.getSubject() == null || request.getSubject().isBlank())) {
            throw new IllegalArgumentException("EMAIL kanalındaki şablonlar için konu (subject) zorunludur.");
        }

        String user = getAuditorUser();

        Optional<NotificationTemplateContent> existingOpt =
                contentRepository.findByTemplate_IdAndLanguageCode(templateId, normalizedLang);

        NotificationTemplateContent content;
        if (existingOpt.isPresent()) {
            content = existingOpt.get();
            content.setSubject(request.getSubject());
            content.setBody(request.getBody());
            content.setUpdatedBy(user);
        } else {
            content = NotificationTemplateContent.builder()
                    .template(template)
                    .languageCode(normalizedLang)
                    .subject(request.getSubject())
                    .body(request.getBody())
                    .updatedBy(user)
                    .build();
        }

        NotificationTemplateContent saved = contentRepository.save(content);

        // İlgili dil ve şablon kodu için eğer çözümlenmemiş eksik logu varsa çözüldü olarak işaretle
        int resolvedLogs = missingTemplateLogRepository.resolveByLocaleAndTemplateCode(
                normalizedLang, template.getTemplateCode(), LocalDateTime.now());

        if (resolvedLogs > 0) {
            log.info("Eksik şablon logları çözümlendi olarak işaretlendi → locale={}, templateCode={}, count={}",
                    normalizedLang, template.getTemplateCode(), resolvedLogs);
        }

        return mapToContentDto(saved);
    }

    // =========================================================================
    // Önizleme Render
    // =========================================================================

    @Transactional(readOnly = true)
    public RenderedTemplateDto previewRender(Long templateId, PreviewRenderRequest request) {
        NotificationTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> NotificationTemplateNotFoundException.byId(templateId));

        // preview render pasif şablonlarda da çalışabilir
        return renderService.renderInternal(
                template.getTemplateCode(),
                request.getLanguageCode(),
                request.getVariables(),
                request.getStrategy(),
                false // onlyActive = false
        );
    }

    // =========================================================================
    // Import & Export İşlemleri
    // =========================================================================

    @Transactional(readOnly = true)
    public byte[] exportAsExcel(String languageCode) throws IOException {
        String normalizedLang = languageCode.toLowerCase(Locale.ROOT);
        validateLanguage(normalizedLang);

        List<NotificationTemplate> templates = templateRepository.findAllByOrderByTemplateCodeAsc();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Templates_" + normalizedLang.toUpperCase());

            // Kolon genişlikleri
            sheet.setColumnWidth(0, 40 * 256);  // Template Code
            sheet.setColumnWidth(1, 15 * 256);  // Channel
            sheet.setColumnWidth(2, 40 * 256);  // Subject
            sheet.setColumnWidth(3, 80 * 256);  // Body
            sheet.setColumnWidth(4, 40 * 256);  // Description
            sheet.setColumnWidth(5, 10 * 256);  // Active

            sheet.createFreezePane(0, 1);

            // Başlık stili (koyu mavi, kalın, beyaz)
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Başlık satırı
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, EXPORT_HEADERS.length - 1));

            // Veri stili
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setWrapText(true);
            dataStyle.setVerticalAlignment(VerticalAlignment.TOP);
            dataStyle.setBorderBottom(BorderStyle.HAIR);

            int rowIdx = 1;
            for (NotificationTemplate t : templates) {
                Optional<NotificationTemplateContent> contentOpt =
                        contentRepository.findByTemplate_IdAndLanguageCode(t.getId(), normalizedLang);

                Row row = sheet.createRow(rowIdx++);

                Cell codeCell = row.createCell(0);
                Cell chanCell = row.createCell(1);
                Cell subjCell = row.createCell(2);
                Cell bodyCell = row.createCell(3);
                Cell descCell = row.createCell(4);
                Cell actCell = row.createCell(5);

                codeCell.setCellValue(t.getTemplateCode());
                chanCell.setCellValue(t.getChannel().name());
                subjCell.setCellValue(contentOpt.map(NotificationTemplateContent::getSubject).orElse(""));
                bodyCell.setCellValue(contentOpt.map(NotificationTemplateContent::getBody).orElse(""));
                descCell.setCellValue(t.getDescription() != null ? t.getDescription() : "");
                actCell.setCellValue(t.isActive() ? "TRUE" : "FALSE");

                codeCell.setCellStyle(dataStyle);
                chanCell.setCellStyle(dataStyle);
                subjCell.setCellStyle(dataStyle);
                bodyCell.setCellStyle(dataStyle);
                descCell.setCellStyle(dataStyle);
                actCell.setCellStyle(dataStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportAsCsv(String languageCode) {
        String normalizedLang = languageCode.toLowerCase(Locale.ROOT);
        validateLanguage(normalizedLang);

        List<NotificationTemplate> templates = templateRepository.findAllByOrderByTemplateCodeAsc();

        StringBuilder sb = new StringBuilder();

        // Başlık satırı
        sb.append(String.join(",", EXPORT_HEADERS)).append("\r\n");

        for (NotificationTemplate t : templates) {
            Optional<NotificationTemplateContent> contentOpt =
                    contentRepository.findByTemplate_IdAndLanguageCode(t.getId(), normalizedLang);

            sb.append(escapeCsv(t.getTemplateCode())).append(",");
            sb.append(escapeCsv(t.getChannel().name())).append(",");
            sb.append(escapeCsv(contentOpt.map(NotificationTemplateContent::getSubject).orElse(""))).append(",");
            sb.append(escapeCsv(contentOpt.map(NotificationTemplateContent::getBody).orElse(""))).append(",");
            sb.append(escapeCsv(t.getDescription() != null ? t.getDescription() : "")).append(",");
            sb.append(escapeCsv(t.isActive() ? "TRUE" : "FALSE")).append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ImportResultDto importTemplates(String languageCode, MultipartFile file) throws IOException {
        String normalizedLang = languageCode.toLowerCase(Locale.ROOT);
        Language language = languageRepository.findByCode(normalizedLang)
                .filter(Language::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dil bulunamadı veya aktif değil: " + languageCode));

        String filename = file.getOriginalFilename();
        boolean isCsv = filename != null && filename.endsWith(".csv");

        List<Map<String, String>> records = isCsv ? parseCsv(file) : parseExcel(file);

        int inserted = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        String auditor = getAuditorUser();

        for (int i = 0; i < records.size(); i++) {
            Map<String, String> row = records.get(i);
            int rowNum = i + 2;

            String code = row.get("templateCode");
            String channelStr = row.get("channel");
            String subject = row.get("subject");
            String body = row.get("body");
            String desc = row.get("description");
            String activeStr = row.get("active");

            if (code == null || code.isBlank()) {
                errors.add("Satır " + rowNum + ": templateCode boş");
                continue;
            }
            code = code.trim();

            if (body == null || body.isBlank()) {
                skipped++;
                continue;
            }
            body = body.trim();

            NotificationChannel channel;
            try {
                channel = NotificationChannel.valueOf(channelStr.trim().toUpperCase());
            } catch (Exception e) {
                errors.add("Satır " + rowNum + ": Geçersiz kanal değeri → " + channelStr);
                continue;
            }

            // EMAIL ise subject zorunludur
            if (channel == NotificationChannel.EMAIL && (subject == null || subject.isBlank())) {
                errors.add("Satır " + rowNum + ": EMAIL kanalı için subject (başlık) zorunludur");
                continue;
            }

            boolean active = activeStr == null || activeStr.trim().equalsIgnoreCase("true");

            NotificationTemplate template;
            Optional<NotificationTemplate> templateOpt = templateRepository.findByTemplateCode(code);

            if (templateOpt.isPresent()) {
                template = templateOpt.get();
                // channel değiştirilemez, ancak desc ve active güncellenebilir
                if (desc != null) template.setDescription(desc);
                template.setActive(active);
                template.setUpdatedBy(auditor);
                templateRepository.save(template);
            } else {
                template = NotificationTemplate.builder()
                        .templateCode(code)
                        .channel(channel)
                        .description(desc)
                        .active(active)
                        .createdBy(auditor)
                        .updatedBy(auditor)
                        .build();
                template = templateRepository.save(template);
            }

            Optional<NotificationTemplateContent> contentOpt =
                    contentRepository.findByTemplate_IdAndLanguageCode(template.getId(), normalizedLang);

            NotificationTemplateContent content;
            if (contentOpt.isPresent()) {
                content = contentOpt.get();
                content.setSubject(subject != null ? subject.trim() : null);
                content.setBody(body);
                content.setUpdatedBy(auditor);
                updated++;
            } else {
                content = NotificationTemplateContent.builder()
                        .template(template)
                        .languageCode(normalizedLang)
                        .subject(subject != null ? subject.trim() : null)
                        .body(body)
                        .updatedBy(auditor)
                        .build();
                inserted++;
            }
            contentRepository.save(content);

            // Eksik loglarını temizle
            missingTemplateLogRepository.resolveByLocaleAndTemplateCode(normalizedLang, code, LocalDateTime.now());
        }

        return ImportResultDto.builder()
                .locale(normalizedLang)
                .totalRows(records.size())
                .insertedCount(inserted)
                .updatedCount(updated)
                .skippedCount(skipped)
                .errors(errors.size() > 100 ? errors.subList(0, 100) : errors)
                .build();
    }

    // =========================================================================
    // Yardımcı Metotlar
    // =========================================================================

    private void validateLanguage(String languageCode) {
        if (!languageRepository.existsByCodeAndActive(languageCode)) {
            throw new IllegalArgumentException("Dil bulunamadı veya aktif değil: " + languageCode);
        }
    }

    private String getAuditorUser() {
        String user = SecurityUtils.currentJwtSubject();
        return user != null ? user : "admin";
    }

    private NotificationTemplateDto mapToDto(NotificationTemplate t) {
        return NotificationTemplateDto.builder()
                .id(t.getId())
                .templateCode(t.getTemplateCode())
                .channel(t.getChannel())
                .description(t.getDescription())
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private NotificationTemplateContentDto mapToContentDto(NotificationTemplateContent c) {
        return NotificationTemplateContentDto.builder()
                .id(c.getId())
                .languageCode(c.getLanguageCode())
                .subject(c.getSubject())
                .body(c.getBody())
                .updatedAt(c.getUpdatedAt())
                .updatedBy(c.getUpdatedBy())
                .build();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private List<Map<String, String>> parseExcel(MultipartFile file) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            boolean isHeader = true;
            for (Row row : sheet) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (isRowEmpty(row)) continue;

                Map<String, String> map = new HashMap<>();
                map.put("templateCode", getCellValue(formatter, row, 0));
                map.put("channel", getCellValue(formatter, row, 1));
                map.put("subject", getCellValue(formatter, row, 2));
                map.put("body", getCellValue(formatter, row, 3));
                map.put("description", getCellValue(formatter, row, 4));
                map.put("active", getCellValue(formatter, row, 5));

                records.add(map);
            }
        }
        return records;
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line = br.readLine(); // Başlık
            if (line == null) return records;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> columns = parseCsvLine(line);
                if (columns.size() < 6) {
                    // Eksik kolonlu satırları dolduralım
                    while (columns.size() < 6) {
                        columns.add("");
                    }
                }

                Map<String, String> map = new HashMap<>();
                map.put("templateCode", columns.get(0));
                map.put("channel", columns.get(1));
                map.put("subject", columns.get(2));
                map.put("body", columns.get(3));
                map.put("description", columns.get(4));
                map.put("active", columns.get(5));

                records.add(map);
            }
        }
        return records;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"'); // escape edilmiş tırnak ""
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim());
        return values;
    }

    private String getCellValue(DataFormatter formatter, Row row, int colIdx) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String val = formatter.formatCellValue(cell).trim();
        return val.isEmpty() ? null : val;
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }
}
