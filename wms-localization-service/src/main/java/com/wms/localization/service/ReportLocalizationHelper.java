package com.wms.localization.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Rapor yerelleştirme yardımcısı.
 *
 * Amaç: PDF (JasperReports), Excel (Apache POI) veya CSV raporu üreten servislere
 * dinamik kolon başlığı ve etiket çevirisi sağlamak.
 *
 * Kullanım örnekleri:
 *
 *   // Tek başlık
 *   String label = helper.getReportLabel("report.stock.column.code", "de");
 *   // → "Lagercode"
 *
 *   // Bir rapor şablonunun tüm başlıkları
 *   Map<String, String> headers = helper.getReportLabels(
 *       List.of("report.stock.column.code",
 *               "report.stock.column.name",
 *               "report.stock.column.quantity"),
 *       "tr");
 *   // → {"report.stock.column.code": "Stok Kodu", ...}
 *
 *   // Rapor şablonu sabitleri ile type-safe erişim
 *   ReportLocalizationHelper.StockReportLabels labels =
 *       helper.stockReportLabels("en");
 *   sheet.setHeader(labels.code(), labels.name(), labels.quantity());
 *
 * Cache stratejisi:
 *   Rapor başlıkları nadiren değişir ama her rapor üretiminde çağrılır.
 *   Bireysel anahtar → 6 saatlik Redis cache (toplu REPORT paketi yerine tekil,
 *   çünkü rapor servisleri genellikle belirli key subset'leri kullanır).
 *
 * Fallback zinciri: locale → default locale → keyCode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportLocalizationHelper {

    private static final String REPORT_CACHE_PREFIX = "report_label:";
    private static final long   REPORT_CACHE_TTL_H  = 6L;

    private final TranslationLookupService         lookupService;
    private final RedisTemplate<String, Object>    redisTemplate;

    // =========================================================================
    // Tek anahtar sorgulama
    // =========================================================================

    /**
     * Tek çeviri anahtarını verilen locale için çözümler.
     *
     * @param keyCode "report.stock.column.code" gibi REPORT modülü anahtarı
     * @param locale  ISO 639-1 dil kodu
     * @return Çeviri metni; bulunamazsa keyCode
     */
    @SuppressWarnings("unchecked")
    public String getReportLabel(String keyCode, String locale) {
        String cacheKey = buildLabelCacheKey(keyCode, locale);

        // 1. Redis'te var mı?
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String value) {
            return value;
        }

        // 2. Cache miss → lookup
        String label = lookupService.getLabel(keyCode, locale);

        // 3. Cache'e yaz (keyCode döndüyse cache'leme — sürekli miss önlenir)
        redisTemplate.opsForValue().set(cacheKey, label, REPORT_CACHE_TTL_H, TimeUnit.HOURS);

        return label;
    }

    // =========================================================================
    // Toplu anahtar sorgulama
    // =========================================================================

    /**
     * Birden fazla anahtarı tek seferde çözümler.
     * Rapor şablonu başlık satırı için kullanılır.
     *
     * Redis pipeline ile çoklu GET — N ayrı roundtrip yerine tek batch.
     *
     * @param keyCodes Anahtar listesi
     * @param locale   ISO 639-1 dil kodu
     * @return keyCode → çeviri metni (ekleme sırası korunur)
     */
    public Map<String, String> getReportLabels(List<String> keyCodes, String locale) {
        if (keyCodes == null || keyCodes.isEmpty()) return Collections.emptyMap();

        Map<String, String> result  = new LinkedHashMap<>();
        List<String>        missing = new ArrayList<>();

        // 1. Redis'ten toplu oku
        for (String keyCode : keyCodes) {
            Object cached = redisTemplate.opsForValue().get(buildLabelCacheKey(keyCode, locale));
            if (cached instanceof String value) {
                result.put(keyCode, value);
            } else {
                missing.add(keyCode);
            }
        }

        // 2. Cache miss olanları DB'den al
        if (!missing.isEmpty()) {
            Map<String, String> fromDb = lookupService.getLabels(missing, locale);
            fromDb.forEach((keyCode, label) -> {
                result.put(keyCode, label);
                // Her birini cache'e yaz
                redisTemplate.opsForValue().set(
                        buildLabelCacheKey(keyCode, locale),
                        label,
                        REPORT_CACHE_TTL_H,
                        TimeUnit.HOURS);
            });
        }

        // Ekleme sırasını koru (keyCodes sırası)
        Map<String, String> ordered = new LinkedHashMap<>();
        keyCodes.forEach(k -> ordered.put(k, result.getOrDefault(k, k)));
        return ordered;
    }

    // =========================================================================
    // Tip-güvenli rapor başlık sabitleri (record)
    // Yeni rapor şablonu eklenince buraya yeni record eklenir.
    // =========================================================================

    /**
     * Stok raporu başlık sabitleri.
     * Kullanım:
     *   var h = helper.stockReportLabels("tr");
     *   row.createCell(0).setCellValue(h.code());
     */
    public StockReportLabels stockReportLabels(String locale) {
        List<String> keys = List.of(
                "report.stock.column.code",
                "report.stock.column.name",
                "report.stock.column.quantity",
                "report.stock.column.unit",
                "report.stock.column.location",
                "report.stock.column.lastMovement"
        );
        Map<String, String> labels = getReportLabels(keys, locale);
        return new StockReportLabels(
                labels.get("report.stock.column.code"),
                labels.get("report.stock.column.name"),
                labels.get("report.stock.column.quantity"),
                labels.get("report.stock.column.unit"),
                labels.get("report.stock.column.location"),
                labels.get("report.stock.column.lastMovement")
        );
    }

    public record StockReportLabels(
            String code,
            String name,
            String quantity,
            String unit,
            String location,
            String lastMovement
    ) {}

    // -------------------------------------------------------------------------

    /**
     * Mal kabul raporu başlık sabitleri.
     */
    public ReceiptReportLabels receiptReportLabels(String locale) {
        List<String> keys = List.of(
                "report.receipt.column.receiptNo",
                "report.receipt.column.supplier",
                "report.receipt.column.receiptDate",
                "report.receipt.column.productCode",
                "report.receipt.column.quantity",
                "report.receipt.column.status"
        );
        Map<String, String> labels = getReportLabels(keys, locale);
        return new ReceiptReportLabels(
                labels.get("report.receipt.column.receiptNo"),
                labels.get("report.receipt.column.supplier"),
                labels.get("report.receipt.column.receiptDate"),
                labels.get("report.receipt.column.productCode"),
                labels.get("report.receipt.column.quantity"),
                labels.get("report.receipt.column.status")
        );
    }

    public record ReceiptReportLabels(
            String receiptNo,
            String supplier,
            String receiptDate,
            String productCode,
            String quantity,
            String status
    ) {}

    // =========================================================================
    // Cache yönetimi
    // =========================================================================

    /**
     * Belirli bir locale'ın rapor başlığı cache'ini temizle.
     * Çeviri import'u sonrası çağrılabilir.
     */
    public void evictReportLabelCache(String locale) {
        Set<String> keys = redisTemplate.keys(REPORT_CACHE_PREFIX + "*:" + locale.toLowerCase());
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Rapor başlığı cache'i temizlendi → locale={}, {} key", locale, keys.size());
        }
    }

    // -------------------------------------------------------------------------

    private String buildLabelCacheKey(String keyCode, String locale) {
        return REPORT_CACHE_PREFIX + keyCode + ":" + locale.toLowerCase(Locale.ROOT);
    }
}
