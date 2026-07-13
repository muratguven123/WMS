package com.wms.localization.service;

import java.util.Map;

/**
 * Çeviri servis sözleşmesi.
 * Implementasyon detayları (Redis, DB, fallback) dışarıya sızmamalıdır.
 */
public interface TranslationService {

    /**
     * Verilen lokasyon + modül için çeviri haritasını döner.
     * İstenen dilde eksik anahtarlar varsayılan dil değerleriyle doldurulur.
     *
     * @param locale  ISO 639-1 dil kodu (tr, en, de …)
     * @param module  Modül adı: UI | REPORT | EMAIL | SYSTEM
     * @return keyCode → çeviri metni eşleşmesi
     */
    Map<String, String> getTranslations(String locale, String module);

    /**
     * Belirtilen lokasyon + modüle ait cache'i geçersiz kılar.
     * Çeviri güncelleme işlemi sonrası çağrılır.
     */
    void evictCache(String locale, String module);

    /**
     * Tüm çeviri cache'ini temizler.
     * Toplu import / migration sonrası kullanılır.
     */
    void evictAllCache();
}
