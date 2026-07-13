package com.wms.localization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.localization.dto.TranslationRowDto;
import com.wms.localization.entity.TranslationValue;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Çeviri Export Servisi.
 *
 * Excel (.xlsx) üretimi Apache POI ile yapılır.
 * JSON üretimi Jackson ObjectMapper ile yapılır.
 *
 * Excel kolon sırası: Key | Modül | Mevcut Çeviri
 * İlk satır başlık (frozen, bold, arka plan renkli).
 * Veriler keyCode'a göre alfabetik sıralıdır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationExportService {

    private static final String[] HEADERS = {"Key", "Modül", "Mevcut Çeviri"};

    private final TranslationValueRepository translationValueRepository;
    private final LanguageRepository         languageRepository;
    private final ObjectMapper               objectMapper;

    // -------------------------------------------------------------------------
    // Excel Export
    // -------------------------------------------------------------------------

    /**
     * Verilen lokasyonun tüm çeviri değerlerini .xlsx olarak üretir.
     *
     * @param locale ISO 639-1 dil kodu
     * @return xlsx dosyasının byte dizisi
     */
    @Transactional(readOnly = true)
    public byte[] exportAsExcel(String locale) throws IOException {
        validateLocale(locale);

        List<TranslationValue> values = translationValueRepository
                .findAllTranslationValuesByLanguageCode(locale);

        log.info("Excel export → locale={}, {} satır", locale, values.size());

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Çeviriler_" + locale.toUpperCase());

            // Kolon genişlikleri (1/256 birim)
            sheet.setColumnWidth(0, 60 * 256);  // Key
            sheet.setColumnWidth(1, 15 * 256);  // Modül
            sheet.setColumnWidth(2, 80 * 256);  // Çeviri

            // Başlık satırını dondur (scroll edildiğinde sabit kalır)
            sheet.createFreezePane(0, 1);

            // Başlık stili
            CellStyle headerStyle = buildHeaderStyle(workbook);

            // Başlık satırı
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Otomatik filtre (Excel'de kolon başlıklarında filtre açılır)
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            // Veri stili — metin wrap
            CellStyle dataStyle = buildDataStyle(workbook);

            // Veri satırları
            int rowIdx = 1;
            for (TranslationValue tv : values) {
                Row row = sheet.createRow(rowIdx++);

                Cell keyCell   = row.createCell(0);
                Cell modCell   = row.createCell(1);
                Cell valueCell = row.createCell(2);

                keyCell.setCellValue(tv.getTranslationKey().getKeyCode());
                modCell.setCellValue(tv.getTranslationKey().getModule());
                valueCell.setCellValue(tv.getValue() != null ? tv.getValue() : "");

                keyCell.setCellStyle(dataStyle);
                modCell.setCellStyle(dataStyle);
                valueCell.setCellStyle(dataStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // -------------------------------------------------------------------------
    // JSON Export
    // -------------------------------------------------------------------------

    /**
     * Verilen lokasyonun çeviri değerlerini JSON byte dizisi olarak üretir.
     * Format: [ { "keyCode": "...", "module": "...", "value": "..." }, ... ]
     */
    @Transactional(readOnly = true)
    public byte[] exportAsJson(String locale) throws IOException {
        validateLocale(locale);

        List<TranslationValue> values = translationValueRepository
                .findAllTranslationValuesByLanguageCode(locale);

        List<TranslationRowDto> rows = values.stream()
                .map(tv -> TranslationRowDto.builder()
                        .keyCode(tv.getTranslationKey().getKeyCode())
                        .module(tv.getTranslationKey().getModule())
                        .value(tv.getValue())
                        .build())
                .toList();

        log.info("JSON export → locale={}, {} satır", locale, rows.size());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows);
    }

    // -------------------------------------------------------------------------
    // Yardımcı metodlar
    // -------------------------------------------------------------------------

    private void validateLocale(String locale) {
        if (!languageRepository.existsByCodeAndActive(locale)) {
            throw new IllegalArgumentException(
                    "Dil bulunamadı veya aktif değil: " + locale);
        }
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // Arka plan — koyu mavi
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Yazı tipi — beyaz, bold
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        // Hizalama
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Kenarlık
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private CellStyle buildDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.HAIR);
        return style;
    }
}
