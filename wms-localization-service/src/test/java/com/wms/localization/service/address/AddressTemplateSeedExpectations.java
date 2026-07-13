package com.wms.localization.service.address;

import java.util.List;
import java.util.stream.Stream;

/**
 * v3 §1 matrisine göre 15 ülke adres şablonu beklentileri.
 * Seed migration doğrulama testlerinde tek kaynak olarak kullanılır.
 */
public final class AddressTemplateSeedExpectations {

    private AddressTemplateSeedExpectations() {}

    public record ExpectedRow(
            String isoCode,
            String fieldKey,
            boolean mandatory,
            int sequence,
            String validationRegex
    ) {}

    public static Stream<ExpectedRow> allRows() {
        return Stream.of(
                // TR — V12_2 + V20
                row("TR", "city", true, 1, null),
                row("TR", "district", true, 2, null),
                row("TR", "neighborhood", true, 3, null),
                row("TR", "street", true, 4, null),
                row("TR", "zip_code", false, 5, "^[0-9]{5}$"),

                // US — V12_2 + V17
                row("US", "state", true, 0, null),
                row("US", "street", true, 1, null),
                row("US", "zip_code", false, 2, "^[0-9]{5}(-[0-9]{4})?$"),

                // DE — V16 + V21
                row("DE", "state", true, 1, null),
                row("DE", "city", true, 2, null),
                row("DE", "street", true, 3, null),
                row("DE", "house_no", true, 4, null),
                row("DE", "zip_code", false, 5, "^[0-9]{5}$"),

                // ES
                row("ES", "state", true, 1, null),
                row("ES", "street", true, 2, null),
                row("ES", "house_no", true, 3, null),
                row("ES", "floor", false, 4, null),
                row("ES", "apartment_no", false, 5, null),
                row("ES", "zip_code", false, 6, "^[0-9]{5}$"),

                // FR — V16 + V21
                row("FR", "state", true, 1, null),
                row("FR", "city", true, 2, null),
                row("FR", "street", true, 3, null),
                row("FR", "house_no", true, 4, null),
                row("FR", "zip_code", false, 5, "^[0-9]{5}$"),

                // IT
                row("IT", "state", true, 1, null),
                row("IT", "street", true, 2, null),
                row("IT", "house_no", true, 3, null),
                row("IT", "zip_code", false, 4, "^[0-9]{5}$"),

                // PT — V16 + V21
                row("PT", "state", true, 1, null),
                row("PT", "city", true, 2, null),
                row("PT", "street", true, 3, null),
                row("PT", "house_no", true, 4, null),
                row("PT", "zip_code", false, 5, "^[0-9]{4}-[0-9]{3}$"),

                // RU
                row("RU", "state", true, 1, null),
                row("RU", "street", true, 2, null),
                row("RU", "house_no", true, 3, null),
                row("RU", "korpus", false, 4, null),
                row("RU", "apartment_no", false, 5, null),
                row("RU", "zip_code", false, 6, "^[0-9]{6}$"),

                // SA
                row("SA", "state", true, 1, null),
                row("SA", "district", true, 2, null),
                row("SA", "street", true, 3, null),
                row("SA", "house_no", true, 4, null),
                row("SA", "additional_no", false, 5, "^[0-9]{4}$"),
                row("SA", "zip_code", false, 6, "^[0-9]{5}$"),

                // CN
                row("CN", "state", true, 1, null),
                row("CN", "district", true, 2, null),
                row("CN", "street", true, 3, null),
                row("CN", "zip_code", false, 4, "^[0-9]{6}$"),

                // JP
                row("JP", "state", true, 1, null),
                row("JP", "chome_banchi", true, 2, null),
                row("JP", "building_name", false, 3, null),
                row("JP", "zip_code", false, 4, "^[0-9]{3}-[0-9]{4}$"),

                // KR
                row("KR", "state", true, 1, null),
                row("KR", "district", true, 2, null),
                row("KR", "road_name", true, 3, null),
                row("KR", "house_no", false, 4, null),
                row("KR", "zip_code", false, 5, "^[0-9]{5}$"),

                // IN
                row("IN", "state", true, 1, null),
                row("IN", "street", true, 2, null),
                row("IN", "locality", false, 3, null),
                row("IN", "landmark", false, 4, null),
                row("IN", "zip_code", false, 5, "^[0-9]{6}$"),

                // NL — V16 + V21
                row("NL", "state", true, 1, null),
                row("NL", "city", true, 2, null),
                row("NL", "street", true, 3, null),
                row("NL", "house_no", true, 4, null),
                row("NL", "zip_code", false, 5, "^[0-9]{4}\\s?[A-Z]{2}$"),

                // PL — V16 + V21
                row("PL", "state", true, 1, null),
                row("PL", "city", true, 2, null),
                row("PL", "street", true, 3, null),
                row("PL", "house_no", true, 4, null),
                row("PL", "zip_code", false, 5, "^[0-9]{2}-[0-9]{3}$")
        );
    }

    public static List<String> supportedIsoCodes() {
        return List.of("TR", "US", "DE", "ES", "FR", "IT", "PT", "RU", "SA", "CN", "JP", "KR", "IN", "NL", "PL");
    }

    private static ExpectedRow row(String iso, String fieldKey, boolean mandatory, int sequence, String regex) {
        return new ExpectedRow(iso, fieldKey, mandatory, sequence, regex);
    }
}
