package com.wms.localization.service.address.formatter;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Adres token temizleme yardımcı sınıfı.
 *
 * <p>Birleştirilecek token listesinden null/blank olanları çıkarır,
 * araya belirtilen ayracı koyarak tek satır üretir.
 * Geriye çirkin çift virgül, baş-sonu boşluk veya ayraç kalıntısı bırakmaz.</p>
 */
final class AddressTokenCleaner {

    private AddressTokenCleaner() { /* utility */ }

    /**
     * Boş olmayan token'ları {@code delimiter} ile birleştirir.
     *
     * @param delimiter ayraç (örn: ", " veya " ")
     * @param tokens    birleştirilecek değerler (null kabul edilir)
     * @return temizlenmiş birleşik string; tüm token'lar boşsa ""
     */
    static String join(String delimiter, String... tokens) {
        return Arrays.stream(tokens)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(delimiter));
    }

    /**
     * JSONB haritasından fieldKey'e karşılık gelen değeri String olarak döner.
     * Key yoksa veya değer null ise {@code null} döner.
     */
    static String get(java.util.Map<String, Object> details, String key) {
        if (details == null) return null;
        Object val = details.get(key);
        return (val != null) ? val.toString().trim() : null;
    }
}
