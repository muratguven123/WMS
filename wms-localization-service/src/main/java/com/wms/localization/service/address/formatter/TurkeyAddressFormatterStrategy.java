package com.wms.localization.service.address.formatter;

import org.springframework.stereotype.Component;

import java.util.Map;

import static com.wms.localization.service.address.formatter.AddressTokenCleaner.get;
import static com.wms.localization.service.address.formatter.AddressTokenCleaner.join;

/**
 * Türkiye adres biçimlendirme stratejisi.
 *
 * <p>Hedef format:</p>
 * <pre>
 *   [neighborhood] Mah. [street] Cad. No:[door_no] D:[apartment_no], [city]/[state]
 * </pre>
 *
 * <p>Eksik alanlar çıkarılır; birleştirme karakterleri temizlenir:</p>
 * <ul>
 *   <li>neighborhood yoksa "Mah." eki de gelmez</li>
 *   <li>door_no yoksa "No:" eki de gelmez</li>
 *   <li>apartment_no yoksa "D:" eki de gelmez</li>
 * </ul>
 */
@Component
public class TurkeyAddressFormatterStrategy implements AddressFormatterStrategy {

    @Override
    public String isoCode() {
        return "TR";
    }

    @Override
    public String format(Map<String, Object> details, String city, String state, String zipCode) {
        String neighborhood = get(details, "neighborhood");
        String street       = get(details, "street");
        String doorNo       = get(details, "door_no");
        String apartmentNo  = get(details, "apartment_no");
        String district     = get(details, "district");

        // Satır 1: mahalle + cadde bloğu
        StringBuilder line1 = new StringBuilder();

        if (neighborhood != null) {
            line1.append(neighborhood).append(" Mah.");
        }
        if (street != null) {
            if (!line1.isEmpty()) line1.append(' ');
            line1.append(street).append(" Cad.");
        }
        if (doorNo != null) {
            if (!line1.isEmpty()) line1.append(' ');
            line1.append("No:").append(doorNo);
        }
        if (apartmentNo != null) {
            if (!line1.isEmpty()) line1.append(' ');
            line1.append("D:").append(apartmentNo);
        }

        // Satır 2: ilçe / şehir / il
        String locationPart = join("/", city, state);
        String districtCity = join(", ", district, locationPart.isEmpty() ? null : locationPart);

        String zipPart = (zipCode != null && !zipCode.isBlank()) ? zipCode.trim() : null;

        return join(", ",
                line1.isEmpty() ? null : line1.toString(),
                districtCity.isEmpty() ? null : districtCity,
                zipPart
        );
    }
}
