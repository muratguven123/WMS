package com.wms.localization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Frontend / Mobil istemciye dönülen aktif format konfigürasyon yanıtı.
 *
 * <p>İstemci bu değerleri kendi tarih/saat kütüphanelerine (moment.js, date-fns,
 * Intl API) ve sayı giriş maskelerine map'ler. Format kalıpları standart
 * Java {@code DateTimeFormatter} / C# {@code DateTime} karakterlerine uyar:</p>
 *
 * <ul>
 *   <li>{@code yyyy} — 4 haneli yıl</li>
 *   <li>{@code MM}   — 2 haneli ay (01-12)</li>
 *   <li>{@code dd}   — 2 haneli gün (01-31)</li>
 *   <li>{@code HH}   — 24 saatlik saat (00-23)</li>
 *   <li>{@code hh}   — 12 saatlik saat (01-12)</li>
 *   <li>{@code mm}   — dakika (00-59)</li>
 *   <li>{@code a}    — AM/PM belirteci</li>
 * </ul>
 *
 * <p>Örnek JSON yanıt (TR lokasyonu):</p>
 * <pre>{@code
 * {
 *   "dateFormat":        "dd.MM.yyyy",
 *   "timeFormat":        "HH:mm",
 *   "decimalSeparator":  ",",
 *   "thousandSeparator": "."
 * }
 * }</pre>
 *
 * @param dateFormat        Java/C# DateTime tarih format deseni
 * @param timeFormat        Java/C# DateTime saat format deseni
 * @param decimalSeparator  Ondalık ayraç karakteri ("," veya ".")
 * @param thousandSeparator Binlik ayraç karakteri ("." veya ",")
 */
public record ActiveFormatResponse(
        @JsonProperty("dateFormat")
        String dateFormat,

        @JsonProperty("timeFormat")
        String timeFormat,

        @JsonProperty("decimalSeparator")
        String decimalSeparator,

        @JsonProperty("thousandSeparator")
        String thousandSeparator
) {
    /**
     * Servis katmanından gelen {@link com.wms.localization.service.ReportFormatterService.ResolvedFormat}
     * nesnesinden dönüşüm için factory metodu.
     */
    public static ActiveFormatResponse of(
            String dateFormat,
            String timeFormat,
            String decimalSeparator,
            String thousandSeparator
    ) {
        return new ActiveFormatResponse(dateFormat, timeFormat, decimalSeparator, thousandSeparator);
    }
}
