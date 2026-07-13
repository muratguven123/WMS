package com.wms.localization.service.address.formatter;


/**
 * countryId Long'sini ISO 3166-1 alpha-2 koduna çeviren port arayüzü.
 *
 * <p>Localization service'in wms-core-service'e doğrudan JPA bağımlılığı
 * almaması için port/adapter deseni uygulanır. Implementasyonlar:</p>
 * <ul>
 *   <li>{@code FeignCountryIsoCodeResolver} — Feign client ile core servis çağrısı</li>
 *   <li>{@code CachedCountryIsoCodeResolver} — Redis/Caffeine cache destekli versiyon</li>
 *   <li>Test ortamında Mockito stub veya sabit map implementasyonu</li>
 * </ul>
 */
public interface CountryIsoCodeResolver {

    /**
     * @param countryId core servisteki Country Long'si
     * @return ISO 3166-1 alpha-2 kodu (örn: "TR", "US"); bilinmiyorsa "UNKNOWN"
     */
    String resolve(Long countryId);
}
