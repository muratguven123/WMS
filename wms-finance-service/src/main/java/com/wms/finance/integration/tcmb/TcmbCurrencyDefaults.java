package com.wms.finance.integration.tcmb;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TCMB senkronizasyonunda otomatik oluşturulacak para birimi metadata'sı.
 * UI {@code CURRENCIES} listesi ve {@link TcmbXmlParser} takip seti ile hizalıdır.
 */
public final class TcmbCurrencyDefaults {

    private record Defaults(String symbol, int decimalPlaces, String name) {}

    private static final Map<String, Defaults> BY_CODE = Map.of(
            "USD", new Defaults("$", 2, "US Dollar"),
            "EUR", new Defaults("€", 2, "Euro"),
            "GBP", new Defaults("£", 2, "British Pound"),
            "SAR", new Defaults("﷼", 2, "Saudi Riyal"),
            "AED", new Defaults("د.إ", 2, "UAE Dirham"),
            "JPY", new Defaults("¥", 0, "Japanese Yen"),
            "CHF", new Defaults("Fr", 2, "Swiss Franc"),
            "CNY", new Defaults("¥", 2, "Chinese Yuan")
    );

    private TcmbCurrencyDefaults() {}

    public static boolean isSupported(String code) {
        return BY_CODE.containsKey(code);
    }

    public static Set<String> trackedCodes() {
        return BY_CODE.keySet();
    }

    public static Optional<Defaults> forCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static String symbol(String code) {
        return forCode(code).map(Defaults::symbol).orElse(code);
    }

    public static int decimalPlaces(String code) {
        return forCode(code).map(Defaults::decimalPlaces).orElse(2);
    }

    public static String name(String code) {
        return forCode(code).map(Defaults::name).orElse(code);
    }
}
