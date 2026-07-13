package com.wms.core.util.timezone;

import com.wms.core.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;

/**
 * Tarih bazlı raporlama filtrelerinde gün kayması (date-shift) önleyici yardımcı.
 *
 * <h3>Problem</h3>
 * Kullanıcı arayüzde "2026-01-10" tarihini seçer. Bu tarihi UTC gün
 * sınırlarına ({@code 00:00Z} - {@code 23:59Z}) göre sorgulamak,
 * Europe/Istanbul (UTC+3) gibi timezone'larda gün başlangıcındaki 3 saatlik
 * işlemleri bir önceki güne kaydırır.
 *
 * <h3>Çözüm</h3>
 * Kullanıcının seçtiği {@link LocalDate}'i lokasyonun IANA timezone'unu baz
 * alarak {@code 00:00:00} ve {@code 23:59:59.999…} yerel anlara dönüştürür,
 * ardından bu anların UTC karşılıklarını {@link InstantRange} olarak döner.
 *
 * <pre>
 * startDate = 2026-01-10, timezone = Europe/Istanbul (UTC+3)
 *
 *   lokal başlangıç : 2026-01-10T00:00:00+03:00
 *   UTC karşılığı   : 2026-01-09T21:00:00Z
 *
 *   lokal bitiş     : 2026-01-10T23:59:59.999999999+03:00
 *   UTC karşılığı   : 2026-01-10T20:59:59.999999999Z
 * </pre>
 */
@Component
public class DateRangeUtcQueryHelper {

    /**
     * Lokasyonun yerel tarih aralığını UTC {@link InstantRange}'e çevirir.
     *
     * <p>DST geçiş günlerinde ZonedDateTime, ilgili geçişi (ileri/geri saat)
     * TZDB üzerinden otomatik uygular — manuel offset hesabı gerekmez.</p>
     *
     * @param startDate        Raporun yerel başlangıç tarihi (dahil)
     * @param endDate          Raporun yerel bitiş tarihi (dahil)
     * @param locationTimezone Lokasyonun IANA timezone string'i (örn: "Europe/Istanbul")
     * @return UTC başlangıç ve bitiş {@code Instant}'larını içeren değer nesnesi
     * @throws BusinessException geçersiz timezone veya null parametre verildiğinde
     */
    public InstantRange convertToUtcRange(LocalDate startDate,
                                          LocalDate endDate,
                                          String locationTimezone) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("startDate ve endDate null olamaz", HttpStatus.BAD_REQUEST);
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(
                    "endDate (%s) startDate'den (%s) önce olamaz".formatted(endDate, startDate),
                    HttpStatus.BAD_REQUEST);
        }

        ZoneId zoneId = parseZoneId(locationTimezone);

        // Yerel gün başlangıcı: 00:00:00.000 — UTC'ye çevir
        ZonedDateTime localStart = startDate.atStartOfDay(zoneId);

        // Yerel gün bitişi: 23:59:59.999999999 — UTC'ye çevir
        // LocalTime.MAX = 23:59:59.999999999 (nanosaniye hassasiyetinde)
        ZonedDateTime localEnd = endDate.atTime(LocalTime.MAX).atZone(zoneId);

        return new InstantRange(localStart.toInstant(), localEnd.toInstant());
    }

    // -------------------------------------------------------------------------

    private ZoneId parseZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new BusinessException("Timezone string boş olamaz", HttpStatus.BAD_REQUEST);
        }
        try {
            return ZoneId.of(timezone);
        } catch (ZoneRulesException e) {
            throw new BusinessException(
                    "Geçersiz IANA timezone: " + timezone, HttpStatus.BAD_REQUEST);
        }
    }
}
