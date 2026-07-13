package com.wms.core.service;

import com.wms.core.entity.Location;
import com.wms.core.entity.User;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;

/**
 * Sunucu tarafı saat dilimi dönüştürme servisi.
 *
 * <p>Veritabanında UTC (Instant / OffsetDateTime) olarak saklanan zaman verilerini,
 * PDF/Excel raporları ve e-posta şablonları için hedef IANA timezone'una dönüştürür.
 * Java Time API'nin ZonedDateTime altyapısı DST (Daylight Saving Time) geçişlerini
 * otomatik olarak hesaba katar — herhangi bir manuel offset işlemi gerekmez.</p>
 *
 * <p><b>Çözümleme önceliği:</b> User.preferredTimezone → Location.timezone</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimezoneService {

    private final UserRepository userRepository;
    private final LocationRepository locationRepository;

    // -------------------------------------------------------------------------
    // Core dönüşüm
    // -------------------------------------------------------------------------

    /**
     * UTC Instant'ı hedef IANA timezone'una çevirerek yerel zamanı döner.
     *
     * <p>DST geçişleri ZonedDateTime tarafından otomatik işlenir:
     * örneğin Europe/Istanbul için kış/yaz saati farkı JVM'in timezone
     * veritabanından (TZDB) alınır.</p>
     *
     * @param utcInstant     DB'den gelen UTC zaman değeri (null olamaz)
     * @param targetTimezone IANA timezone string — örn: "Europe/Istanbul"
     * @return Hedef timezone'da yerel zaman (offset bilgisi taşımaz)
     * @throws BusinessException geçersiz timezone string verildiğinde
     */
    public LocalDateTime convertToLocalTime(Instant utcInstant, String targetTimezone) {
        if (utcInstant == null) {
            throw new BusinessException("utcInstant null olamaz", HttpStatus.BAD_REQUEST);
        }

        ZoneId zoneId = parseZoneId(targetTimezone);
        ZonedDateTime zonedDateTime = utcInstant.atZone(zoneId);

        log.debug("UTC {} → {} ({})", utcInstant, zonedDateTime.toLocalDateTime(), targetTimezone);

        return zonedDateTime.toLocalDateTime();
    }

    // -------------------------------------------------------------------------
    // Bağlamsal çözümleme
    // -------------------------------------------------------------------------

    /**
     * Aktif kullanıcı ve depo bağlamına göre hedef timezone'u çözer.
     *
     * <p>Öncelik sırası:</p>
     * <ol>
     *   <li>User.preferredTimezone (null değilse ve boş değilse)</li>
     *   <li>Location.timezone (her zaman dolu olmalı — DB constraint)</li>
     * </ol>
     *
     * @param userId     Aktif kullanıcının Long'si
     * @param locationId Bağlam deposunun Long'si
     * @return Çözümlenen IANA timezone string
     * @throws BusinessException kullanıcı veya depo bulunamazsa
     */
    public String resolveTargetTimezone(Long userId, Long locationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "Kullanıcı bulunamadı: " + userId, HttpStatus.NOT_FOUND));

        String preferred = user.getPreferredTimezone();
        if (preferred != null && !preferred.isBlank()) {
            log.debug("Timezone çözümlendi — kullanıcı tercihi: {} (userId={})", preferred, userId);
            return preferred;
        }

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new BusinessException(
                        "Lokasyon bulunamadı: " + locationId, HttpStatus.NOT_FOUND));

        log.debug("Timezone çözümlendi — lokasyon fallback: {} (locationId={})",
                location.getTimezone(), locationId);

        return location.getTimezone();
    }

    /**
     * resolveTargetTimezone ile convertToLocalTime'ı birleştiren kolaylık metodu.
     * Raporlama katmanında tek çağrıyla yerel zaman elde etmek için kullanılır.
     *
     * @param utcInstant UTC zaman değeri
     * @param userId     Aktif kullanıcı
     * @param locationId Bağlam deposu
     * @return Çözümlenen timezone'da yerel zaman
     */
    public LocalDateTime convertToLocalTime(Instant utcInstant, Long userId, Long locationId) {
        String timezone = resolveTargetTimezone(userId, locationId);
        return convertToLocalTime(utcInstant, timezone);
    }

    // -------------------------------------------------------------------------
    // Yardımcı
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
