package com.wms.localization.integration;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Geliştirme/test ortamı için sabit lokasyon → ülke eşlemesi.
 *
 * <p>Üretimde {@code CoreServiceLocationCountryResolver} (Feign/RestClient) ile
 * değiştirilmelidir.</p>
 */
@Component
public class StubLocationCountryResolver implements LocationCountryResolver {

    /** wms-core demo lokasyonu (V6 workflow / integration seed). */
    public static final Long DEMO_LOCATION_ID = 101L;

    /** Demo Türkiye ülke kaydı (V6 format seed). */
    public static final Long DEMO_COUNTRY_TR_ID = 1L;

    private static final Map<Long, Long> LOCATION_TO_COUNTRY = Map.of(
            DEMO_LOCATION_ID, DEMO_COUNTRY_TR_ID
    );

    @Override
    public Optional<Long> resolveCountryId(Long locationId) {
        if (locationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOCATION_TO_COUNTRY.get(locationId));
    }
}
