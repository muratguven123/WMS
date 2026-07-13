package com.wms.core.integration;

import java.util.Optional;

/**
 * localization-service'teki ülke kullanımını sorgulayan port arayüzü
 * (İş İsteri 18 — Teknik İş Kuralı 2: pasifleştirme öncesi cross-service kontrol).
 *
 * <p>Country id'si localization tarafında FK'siz (cross-service) referans
 * olduğu için tutarlılık uygulama seviyesinde bu kontrol ile sağlanır.
 * Kontrol best-effort'tur: servis ulaşılamazsa {@link Optional#empty()}
 * döner ve pasifleştirme kullanıcı onayıyla devam edebilir.</p>
 */
public interface LocalizationUsageClient {

    /**
     * Ülkenin localization-service'teki kullanımını döner.
     *
     * @param countryId core Country id'si
     * @return kullanım özeti; servis ulaşılamazsa empty
     */
    Optional<CountryUsage> fetchUsage(Long countryId);

    /**
     * @param addressCount   ülkeye bağlı adres kaydı sayısı
     * @param templateExists ülkenin adres şablonu tanımlı mı (İş İsteri 17)
     */
    record CountryUsage(long addressCount, boolean templateExists) {}
}
