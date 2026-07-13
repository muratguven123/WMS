package com.wms.localization.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.localization.dto.ImportResultDto;
import com.wms.localization.dto.TranslationRowDto;
import com.wms.localization.entity.Language;
import com.wms.localization.entity.TranslationKey;
import com.wms.localization.entity.TranslationValue;
import com.wms.localization.repository.LanguageRepository;
import com.wms.localization.repository.TranslationKeyRepository;
import com.wms.localization.repository.TranslationValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Çeviri Import Servisi.
 *
 * Desteklenen formatlar: .xlsx / .json
 *
 * İşlem akışı:
 *  1. Dosya formatını belirle (Content-Type veya uzantı).
 *  2. Parse et → List<TranslationRowDto>
 *  3. Her satır için:
 *     a. TranslationKey var mı kontrol et — yoksa hata kaydı (import yaratmaz).
 *     b. TranslationValue (locale, key) var mı?
 *        - Varsa → güncelle (UPDATE)
 *        - Yoksa → yeni kayıt (INSERT)
 *  4. Batch flush (her 500 kayıtta bir saveAll → bellek yönetimi).
 *  5. Başarıyla tamamlandığında o locale'a ait Redis cache'i temizle.
 *  6. ImportResultDto döndür.
 *
 * Güvenlik notu: value boş/null ise kayıt atlanır (skipped), silinmez.
 * Bu tasarım kararı: yönetici yanlışlıkla çevirileri silemesin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationImportService {

    private static final int BATCH_SIZE = 500;

    // Excel kolon indeksleri (export ile uyumlu)
    private static final int COL_KEY    = 0;
    private static final int COL_MODULE = 1;
    private static final int COL_VALUE  = 2;

    private final TranslationValueRepository translationValueRepository;
    private final TranslationKeyRepository   translationKeyRepository;
    private final LanguageRepository         languageRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper               objectMapper;
    private final ReportLocalizationHelper   reportLocalizationHelper;

    // -------------------------------------------------------------------------
    // Ana import metodu
    // -------------------------------------------------------------------------

    @Transactional
    public ImportResultDto importTranslations(String locale, MultipartFile file) throws IOException {
        // Dil varlığını doğrula
        Language language = languageRepository.findByCode(locale)
                .filter(Language::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dil bulunamadı veya aktif değil: " + locale));

        // Dosyayı parse et
        List<TranslationRowDto> rows = parseFile(file);
        log.info("Import başlıyor → locale={}, format={}, {} satır",
                locale, detectFormat(file), rows.size());

        // Upsert işlemi
        ImportResultDto result = upsertTranslations(language, rows);

        // Cache eviction — başarılıysa
        boolean evicted = evictLocaleCache(locale);
        reportLocalizationHelper.evictReportLabelCache(locale);
        result.setCacheEvicted(evicted);

        log.info("Import tamamlandı → locale={}, insert={}, update={}, skip={}, hata={}",
                locale, result.getInsertedCount(), result.getUpdatedCount(),
                result.getSkippedCount(), result.getErrors().size());

        return result;
    }

    // -------------------------------------------------------------------------
    // Upsert mantığı
    // -------------------------------------------------------------------------

    private ImportResultDto upsertTranslations(Language language, List<TranslationRowDto> rows) {
        int inserted = 0, updated = 0, skipped = 0;
        List<String> errors   = new ArrayList<>();
        List<TranslationValue> batch = new ArrayList<>(BATCH_SIZE);

        for (int i = 0; i < rows.size(); i++) {
            TranslationRowDto row = rows.get(i);
            int lineNumber = i + 2; // Başlık satırı = 1, veri = 2'den başlar

            // Boş value → atla
            if (row.getValue() == null || row.getValue().isBlank()) {
                skipped++;
                continue;
            }

            // keyCode zorunlu
            if (row.getKeyCode() == null || row.getKeyCode().isBlank()) {
                errors.add("Satır " + lineNumber + ": keyCode boş");
                continue;
            }

            // TranslationKey DB'de var mı?
            Optional<TranslationKey> keyOpt =
                    translationKeyRepository.findByKeyCode(row.getKeyCode().trim());

            if (keyOpt.isEmpty()) {
                errors.add("Satır " + lineNumber + ": Anahtar bulunamadı → " + row.getKeyCode());
                continue;
            }

            TranslationKey translationKey = keyOpt.get();

            // Mevcut TranslationValue var mı?
            Optional<TranslationValue> existingOpt =
                    translationValueRepository.findByLanguageCodeAndKeyCode(
                            language.getCode(), translationKey.getKeyCode());

            TranslationValue tv;
            if (existingOpt.isPresent()) {
                // UPDATE
                tv = existingOpt.get();
                tv.setValue(row.getValue().trim());
                updated++;
            } else {
                // INSERT
                tv = TranslationValue.builder()
                        .language(language)
                        .translationKey(translationKey)
                        .value(row.getValue().trim())
                        .build();
                inserted++;
            }

            batch.add(tv);

            // Batch flush
            if (batch.size() >= BATCH_SIZE) {
                translationValueRepository.saveAll(batch);
                batch.clear();
            }
        }

        // Kalan batch
        if (!batch.isEmpty()) {
            translationValueRepository.saveAll(batch);
        }

        return ImportResultDto.builder()
                .locale(language.getCode())
                .totalRows(rows.size())
                .insertedCount(inserted)
                .updatedCount(updated)
                .skippedCount(skipped)
                .errors(errors.size() > 100 ? errors.subList(0, 100) : errors)
                .build();
    }

    // -------------------------------------------------------------------------
    // Cache eviction
    // -------------------------------------------------------------------------

    /**
     * "translations:{locale}:*" pattern'iyle eşleşen tüm Redis key'lerini siler.
     * Bu sayede o dile ait her modülün cache'i temizlenir.
     */
    private boolean evictLocaleCache(String locale) {
        try {
            String pattern = "translations:" + locale.toLowerCase(Locale.ROOT) + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info("Cache eviction → pattern={}, {} key silindi", pattern, deleted);
            } else {
                log.debug("Cache eviction → pattern={}, silinecek key yok", pattern);
            }
            return true;
        } catch (Exception e) {
            log.error("Cache eviction başarısız → locale={}, hata: {}", locale, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Dosya parse — format tespiti ve delegasyon
    // -------------------------------------------------------------------------

    private List<TranslationRowDto> parseFile(MultipartFile file) throws IOException {
        String format = detectFormat(file);
        return switch (format) {
            case "xlsx" -> parseExcel(file.getInputStream());
            case "json" -> parseJson(file.getInputStream());
            default     -> throw new IllegalArgumentException(
                    "Desteklenmeyen dosya formatı. .xlsx veya .json yükleyin.");
        };
    }

    private String detectFormat(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            if (originalName.endsWith(".xlsx")) return "xlsx";
            if (originalName.endsWith(".json")) return "json";
        }
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.contains("spreadsheet") || contentType.contains("excel")) return "xlsx";
            if (contentType.contains("json")) return "json";
        }
        return "unknown";
    }

    // -------------------------------------------------------------------------
    // Excel Parser
    // -------------------------------------------------------------------------

    /**
     * Apache POI ile Excel'i okur.
     * İlk satır başlık — atlanır.
     * Kolon sırası: Key(0) | Modül(1) | Çeviri(2)
     */
    private List<TranslationRowDto> parseExcel(InputStream inputStream) throws IOException {
        List<TranslationRowDto> rows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // İlk satır başlık (skip)
            boolean firstRow = true;
            for (Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    continue;
                }

                // Tamamen boş satırları atla
                if (isRowEmpty(row)) continue;

                String keyCode = getCellValue(formatter, row, COL_KEY);
                String module  = getCellValue(formatter, row, COL_MODULE);
                String value   = getCellValue(formatter, row, COL_VALUE);

                rows.add(TranslationRowDto.builder()
                        .keyCode(keyCode)
                        .module(module)
                        .value(value)
                        .build());
            }
        }
        return rows;
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

    // -------------------------------------------------------------------------
    // JSON Parser
    // -------------------------------------------------------------------------

    private List<TranslationRowDto> parseJson(InputStream inputStream) throws IOException {
        return objectMapper.readValue(inputStream, new TypeReference<>() {});
    }
}
