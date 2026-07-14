package com.wms.core.util;

import com.wms.core.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * UI kural motoru string boyut kodları (customerType, productType,
 * transactionStatus) için normalizasyon ve doğrulama yardımcıları.
 *
 * <p>Bu boyutlar açık kod kümeleridir (enum değil): yeni müşteri/ürün tipleri
 * kod değişikliği gerektirmeden tanımlanabilir. Tutarlı eşleşme için hem kural
 * yazma yolunda (UiRuleManagementService / TableColumnAdminService) hem de
 * bağlam okuma yolunda (UiContextFactory) aynı normalizasyon uygulanır:
 * trim → boş ise null → UPPER CASE.</p>
 */
public final class DimensionCode {

    /** İzin verilen kod deseni: büyük harfle başlar, büyük harf/rakam/altçizgi, ≤50. */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,49}$");

    private DimensionCode() {
    }

    /**
     * Trim'ler, boş ise null döner, aksi halde UPPER CASE'e çevirir.
     * Doğrulama yapmaz — inbound {@code UiContext} boyutları için kullanılır
     * (geçersiz bağlam değeri hata değildir; hiçbir kurala eşleşmez).
     */
    public static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * Normalize eder ve kural kaydı için desen doğrulaması yapar.
     * Kural yazma yolunda kullanılır — geçersiz kod kalıcılaşmamalıdır.
     *
     * @param raw       ham değer (null olabilir → null döner)
     * @param fieldName hata mesajında kullanılacak alan adı
     * @return normalize edilmiş kod veya null
     * @throws BusinessException desen ihlalinde → HTTP 400, {@code UI_RULE_INVALID_DIMENSION}
     */
    public static String normalizeAndValidate(String raw, String fieldName) {
        String normalized = normalizeOrNull(raw);
        if (normalized == null) {
            return null;
        }
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(
                    ("%s geçersiz: '%s'. Beklenen desen: büyük harfle başlayan, "
                     + "en fazla 50 karakterlik BÜYÜK_HARF/rakam/altçizgi kodu — örn: RETAIL, COLD_CHAIN")
                            .formatted(fieldName, raw),
                    HttpStatus.BAD_REQUEST,
                    "UI_RULE_INVALID_DIMENSION");
        }
        return normalized;
    }
}
