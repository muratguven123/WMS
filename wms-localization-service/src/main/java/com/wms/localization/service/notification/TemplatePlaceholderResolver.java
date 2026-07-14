package com.wms.localization.service.notification;

import com.wms.localization.dto.ActiveFormatResponse;
import com.wms.localization.exception.notification.PlaceholderResolutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {{variable}} placeholder çözümleyici.
 *
 * <h3>Söz Dizimi</h3>
 * <pre>{@code {{key}} veya {{ key }} — anahtar: harf/rakam/nokta/alt çizgi}</pre>
 *
 * <h3>Değer Biçimlendirme</h3>
 * Tarih ve sayı tipindeki değişkenler, FormatConfigService'ten çözümlenen
 * {@link ActiveFormatResponse} (CountryFormatConfig / LocationFormatOverride
 * hiyerarşisi) desenleriyle biçimlendirilir:
 * <ul>
 *   <li>{@link Instant}       → dateFormat + " " + timeFormat (UTC zone)</li>
 *   <li>{@link LocalDateTime} → dateFormat + " " + timeFormat</li>
 *   <li>{@link LocalDate}     → dateFormat</li>
 *   <li>{@link LocalTime}     → timeFormat</li>
 *   <li>{@link Number}        → decimal/thousand separator
 *       (ReportFormatterService.formatNumber ile aynı kurallar)</li>
 *   <li>Diğer tipler          → {@code String.valueOf}</li>
 * </ul>
 *
 * <h3>Çözülemeyen Placeholder</h3>
 * Değişken map'inde bulunmayan (veya değeri null olan) anahtarlar loglanır ve
 * {@link UnresolvedPlaceholderStrategy}'ye göre işlenir. Stateless bileşendir —
 * unit testlerde doğrudan {@code new} ile kullanılabilir.
 */
@Slf4j
@Component
public class TemplatePlaceholderResolver {

    /** {{ key }} — anahtar: harf/rakam/nokta/alt çizgi; çevresinde opsiyonel boşluk. */
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*\\}\\}");

    /**
     * Metindeki tüm placeholder'ları çözer.
     *
     * @param text      Şablon metni (subject veya body); null ise boş sonuç döner
     * @param variables Değişken map'i; null kabul edilir (tümü çözülemez sayılır)
     * @param format    Tarih/sayı biçimlendirme desenleri
     * @param strategy  Çözülemeyen placeholder stratejisi
     * @return Çözümlenmiş metin + çözülemeyen anahtar listesi
     * @throws PlaceholderResolutionException strateji FAIL ise ve çözülemeyen varsa
     */
    public ResolvedText resolve(String text,
                                Map<String, Object> variables,
                                ActiveFormatResponse format,
                                UnresolvedPlaceholderStrategy strategy) {
        if (text == null || text.isEmpty()) {
            return new ResolvedText("", List.of());
        }

        Map<String, Object> vars = variables != null ? variables : Map.of();
        List<String> unresolved  = new ArrayList<>();

        Matcher matcher  = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key   = matcher.group(1);
            Object value = vars.get(key);

            String replacement;
            if (value == null) {
                unresolved.add(key);
                // FAIL: tüm metin taranıp eksiksiz liste toplandıktan sonra fırlatılır
                replacement = (strategy == UnresolvedPlaceholderStrategy.KEEP)
                        ? matcher.group(0)   // {{key}} olduğu gibi kalır
                        : "";                // BLANK ve FAIL: boş
            } else {
                replacement = formatValue(value, format);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        if (!unresolved.isEmpty()) {
            log.warn("[UNRESOLVED_PLACEHOLDER] {} placeholder çözülemedi → {} (strateji={})",
                     unresolved.size(), unresolved, strategy);
            if (strategy == UnresolvedPlaceholderStrategy.FAIL) {
                throw new PlaceholderResolutionException(unresolved);
            }
        }

        return new ResolvedText(sb.toString(), List.copyOf(unresolved));
    }

    // =========================================================================
    // Değer biçimlendirme
    // =========================================================================

    /**
     * Değişken değerini, deponun format kurallarına göre string'e çevirir.
     * ReportFormatterService ile aynı biçimlendirme kuralları uygulanır.
     */
    public String formatValue(Object value, ActiveFormatResponse format) {
        if (value instanceof Instant instant) {
            return dateTimeFormatter(format).format(instant);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.format(patternFormatter(
                    format.dateFormat() + " " + format.timeFormat()));
        }
        if (value instanceof LocalDate localDate) {
            return localDate.format(patternFormatter(format.dateFormat()));
        }
        if (value instanceof LocalTime localTime) {
            return localTime.format(patternFormatter(format.timeFormat()));
        }
        if (value instanceof BigDecimal bigDecimal) {
            return formatNumber(bigDecimal, format);
        }
        if (value instanceof Number number) {
            return formatNumber(new BigDecimal(number.toString()), format);
        }
        return String.valueOf(value);
    }

    // -------------------------------------------------------------------------

    /** Instant için: UTC zone sabitlenir — çağıran servis timezone dönüşümünü kendisi yapmalıdır. */
    private DateTimeFormatter dateTimeFormatter(ActiveFormatResponse format) {
        String pattern = format.dateFormat() + " " + format.timeFormat();
        return patternFormatter(pattern).withZone(ZoneOffset.UTC);
    }

    /** AM/PM ("a") içeren desenlerde İngilizce locale — ReportFormatterService ile tutarlı. */
    private DateTimeFormatter patternFormatter(String pattern) {
        return pattern.contains("a")
                ? DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                : DateTimeFormatter.ofPattern(pattern);
    }

    private String formatNumber(BigDecimal amount, ActiveFormatResponse format) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(format.decimalSeparator().charAt(0));
        symbols.setGroupingSeparator(format.thousandSeparator().charAt(0));

        int fractionDigits = amount.stripTrailingZeros().scale() > 0 ? 2 : 0;
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.##", symbols);
        decimalFormat.setMinimumFractionDigits(fractionDigits);
        decimalFormat.setMaximumFractionDigits(fractionDigits);

        return decimalFormat.format(amount);
    }

    // =========================================================================
    // Value Object
    // =========================================================================

    /**
     * Çözümleme sonucu: nihai metin + çözülemeyen placeholder anahtarları.
     */
    public record ResolvedText(String text, List<String> unresolvedPlaceholders) {}
}
