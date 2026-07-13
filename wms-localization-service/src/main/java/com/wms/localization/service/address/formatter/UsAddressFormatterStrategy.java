package com.wms.localization.service.address.formatter;

import org.springframework.stereotype.Component;

import java.util.Map;

import static com.wms.localization.service.address.formatter.AddressTokenCleaner.get;
import static com.wms.localization.service.address.formatter.AddressTokenCleaner.join;

/**
 * Amerika Birleşik Devletleri adres biçimlendirme stratejisi.
 *
 * <p>Hedef format:</p>
 * <pre>
 *   [street] St, [city], [state] [zipCode], USA
 * </pre>
 *
 * <p>Eksik alanlar ve bitişik ayraçlar temizlenir:</p>
 * <ul>
 *   <li>street yoksa "St" eki de gelmez</li>
 *   <li>zipCode yoksa state'e yapışmaz, boşluk kalmaz</li>
 * </ul>
 */
@Component
public class UsAddressFormatterStrategy implements AddressFormatterStrategy {

    @Override
    public String isoCode() {
        return "US";
    }

    @Override
    public String format(Map<String, Object> details, String city, String state, String zipCode) {
        String street  = get(details, "street");
        String poBox   = get(details, "po_box");

        // Adres satırı: sokak veya PO Box
        String addressLine;
        if (street != null) {
            addressLine = street + " St";
        } else if (poBox != null) {
            addressLine = "PO Box " + poBox;
        } else {
            addressLine = null;
        }

        // state + zip birlikte (örn: "CA 90210"); zip yoksa sadece state
        String stateZip = join(" ",
                (state != null && !state.isBlank()) ? state.trim() : null,
                (zipCode != null && !zipCode.isBlank()) ? zipCode.trim() : null
        );

        return join(", ",
                addressLine,
                (city != null && !city.isBlank()) ? city.trim() : null,
                stateZip.isEmpty() ? null : stateZip,
                "USA"
        );
    }
}
