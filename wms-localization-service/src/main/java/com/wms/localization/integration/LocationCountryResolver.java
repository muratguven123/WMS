package com.wms.localization.integration;

import java.util.Optional;

/**
 * Depo (Location) → Ülke (Country) ilişkisini çözen servis portu.
 *
 * <p>wms-localization-service, Country bilgisine doğrudan erişemez çünkü bu veri
 * wms-core-service'e aittir. Bu port (arayüz) ikisi arasındaki sözleşmeyi tanımlar;
 * gerçek implementasyon ayrı bir adaptör sınıfı olarak sağlanır:</p>
 *
 * <ul>
 *   <li><b>Üretim:</b> {@code CoreServiceLocationCountryResolver} — Feign / RestClient
 *       üzerinden wms-core-service'e HTTP çağrısı yapar.</li>
 *   <li><b>Test / Geliştirme:</b> {@code StubLocationCountryResolver} — sabit
 *       Long eşlemesiyle in-memory çalışır, dış bağımlılık gerektirmez.</li>
 * </ul>
 *
 * <h3>Kullanım</h3>
 * <pre>{@code
 * // Servis katmanında
 * Optional<Long> countryId = resolver.resolveCountryId(locationId);
 * countryId.ifPresent(id -> formatConfigService.findByCountry(id));
 * }</pre>
 */
public interface LocationCountryResolver {

    /**
     * Verilen depo için bağlı ülkenin Long'sini döner.
     *
     * @param locationId wms-core-service Location entity Long'si
     * @return Ülke Long'si; depo bulunamazsa veya uzak servis yanıt vermezse
     *         {@link Optional#empty()} — çağıran taraf fall-back uygulamalıdır
     */
    Optional<Long> resolveCountryId(Long locationId);
}
