package com.wms.core.util.timezone;

import java.time.Instant;

/**
 * UTC aralık değer nesnesi.
 *
 * <p>Lokasyonun yerel tarih aralığından türetilen UTC başlangıç/bitiş çifti.
 * JPA Specification veya JPQL sorgu parametresi olarak doğrudan kullanılır.</p>
 *
 * <p>Örnek — Europe/Istanbul (UTC+3) için 2026-01-10:</p>
 * <pre>
 *   start = 2026-01-09T21:00:00Z   (lokal 00:00:00 → UTC)
 *   end   = 2026-01-10T20:59:59.999999999Z   (lokal 23:59:59.999… → UTC)
 * </pre>
 *
 * @param start Yerel günün 00:00:00'ının UTC karşılığı (dahil)
 * @param end   Yerel günün bitiş anının UTC karşılığı (dahil)
 */
public record InstantRange(Instant start, Instant end) {

    public InstantRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("InstantRange: start ve end null olamaz");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "InstantRange: start (%s) end'den (%s) sonra olamaz".formatted(start, end));
        }
    }
}
