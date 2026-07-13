package com.wms.localization.integration;

import com.wms.localization.service.address.formatter.CountryIsoCodeResolver;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Geliştirme/test ortamı için sabit countryId → ISO kod eşlemesi.
 *
 * <p>Üretimde core servisten ülke bilgisi çeken bir Feign/RestClient implementasyonu
 * ile değiştirilmelidir.</p>
 */
@Component
public class StubCountryIsoCodeResolver implements CountryIsoCodeResolver {

    /** wms-core V9 demo — Türkiye */
    public static final Long COUNTRY_TR_ID = 1L;

    /** wms-core V9 demo — ABD */
    public static final Long COUNTRY_US_ID = 10L;

    /** wms-core V6 demo — Türkiye (format/workflow seed); BIGINT migration sonrası 1L ile aynı */
    public static final Long DEMO_COUNTRY_TR_ID = COUNTRY_TR_ID;

    private static final Map<Long, String> COUNTRY_TO_ISO = Map.of(
            COUNTRY_TR_ID, "TR",
            COUNTRY_US_ID, "US"
    );

    @Override
    public String resolve(Long countryId) {
        if (countryId == null) {
            return "UNKNOWN";
        }
        return COUNTRY_TO_ISO.getOrDefault(countryId, "UNKNOWN");
    }
}
