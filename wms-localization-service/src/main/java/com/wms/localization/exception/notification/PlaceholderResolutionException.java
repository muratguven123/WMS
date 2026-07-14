package com.wms.localization.exception.notification;

import lombok.Getter;

import java.util.List;

/**
 * {@code UnresolvedPlaceholderStrategy.FAIL} stratejisinde, şablondaki bir veya
 * daha fazla placeholder çözülemediğinde fırlatılır.
 * GlobalExceptionHandler tarafından 422 + UNRESOLVED_PLACEHOLDER koduna map edilir.
 */
@Getter
public class PlaceholderResolutionException extends RuntimeException {

    /** Çözülemeyen placeholder anahtarları. */
    private final List<String> placeholders;

    public PlaceholderResolutionException(List<String> placeholders) {
        super("Çözülemeyen placeholder(lar): " + String.join(", ", placeholders));
        this.placeholders = List.copyOf(placeholders);
    }
}
