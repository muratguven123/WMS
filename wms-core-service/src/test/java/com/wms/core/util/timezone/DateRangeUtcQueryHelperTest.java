package com.wms.core.util.timezone;

import com.wms.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.*;

/**
 * DateRangeUtcQueryHelper birim testleri.
 *
 * Temel senaryo: UI'dan gelen yerel tarih filtresi UTC'ye çevrilirken
 * gün kayması (date-shift) oluşmadığı doğrulanır.
 */
@DisplayName("DateRangeUtcQueryHelper")
class DateRangeUtcQueryHelperTest {

    private DateRangeUtcQueryHelper helper;

    @BeforeEach
    void setUp() {
        helper = new DateRangeUtcQueryHelper();
    }

    // =========================================================================
    // Temel dönüşüm doğruluğu
    // =========================================================================

    @Nested
    @DisplayName("convertToUtcRange — temel dönüşüm")
    class BasicConversion {

        @Test
        @DisplayName("Europe/Istanbul (UTC+3): 2026-01-10 → start=2026-01-09T21:00Z, end=2026-01-10T20:59:59.999…Z")
        void istanbulSingleDay() {
            LocalDate date = LocalDate.of(2026, Month.JANUARY, 10);

            InstantRange range = helper.convertToUtcRange(date, date, "Europe/Istanbul");

            // Lokal 00:00:00 → UTC: −3 saat = 2026-01-09T21:00:00Z
            assertThat(range.start()).isEqualTo(Instant.parse("2026-01-09T21:00:00Z"));
            // Lokal 23:59:59.999999999 → UTC: −3 saat = 2026-01-10T20:59:59.999999999Z
            assertThat(range.end()).isEqualTo(Instant.parse("2026-01-10T20:59:59.999999999Z"));
        }

        @Test
        @DisplayName("UTC timezone: sınırlar doğrudan gün başlangıcı/bitişidir")
        void utcTimezone() {
            LocalDate date = LocalDate.of(2026, Month.JUNE, 15);

            InstantRange range = helper.convertToUtcRange(date, date, "UTC");

            assertThat(range.start()).isEqualTo(Instant.parse("2026-06-15T00:00:00Z"));
            assertThat(range.end()).isEqualTo(Instant.parse("2026-06-15T23:59:59.999999999Z"));
        }

        @Test
        @DisplayName("America/New_York (EDT, UTC-4): 2026-07-04 → start=2026-07-04T04:00Z, end=2026-07-05T03:59:59.999…Z")
        void newYorkSummer() {
            LocalDate date = LocalDate.of(2026, Month.JULY, 4);

            InstantRange range = helper.convertToUtcRange(date, date, "America/New_York");

            assertThat(range.start()).isEqualTo(Instant.parse("2026-07-04T04:00:00Z"));
            assertThat(range.end()).isEqualTo(Instant.parse("2026-07-05T03:59:59.999999999Z"));
        }

        @Test
        @DisplayName("America/New_York (EST, UTC-5): 2026-01-15 → start=2026-01-15T05:00Z, end=2026-01-16T04:59:59.999…Z")
        void newYorkWinter() {
            LocalDate date = LocalDate.of(2026, Month.JANUARY, 15);

            InstantRange range = helper.convertToUtcRange(date, date, "America/New_York");

            assertThat(range.start()).isEqualTo(Instant.parse("2026-01-15T05:00:00Z"));
            assertThat(range.end()).isEqualTo(Instant.parse("2026-01-16T04:59:59.999999999Z"));
        }

        @Test
        @DisplayName("Çok günlük aralık: startDate ve endDate farklı olduğunda aralık doğru hesaplanır")
        void multiDayRange() {
            LocalDate start = LocalDate.of(2026, Month.JANUARY, 10);
            LocalDate end   = LocalDate.of(2026, Month.JANUARY, 12);

            InstantRange range = helper.convertToUtcRange(start, end, "Europe/Istanbul");

            // 10 Ocak 00:00 Istanbul → UTC
            assertThat(range.start()).isEqualTo(Instant.parse("2026-01-09T21:00:00Z"));
            // 12 Ocak 23:59:59.999 Istanbul → UTC
            assertThat(range.end()).isEqualTo(Instant.parse("2026-01-12T20:59:59.999999999Z"));
        }
    }

    // =========================================================================
    // Gün kayması (date-shift) önleme — temel senaryo doğrulaması
    // =========================================================================

    @Nested
    @DisplayName("Gün kayması önleme senaryosu")
    class DateShiftPrevention {

        @Test
        @DisplayName("Istanbul: 10 Ocak 00:00–03:00 arası işlemler 9 Ocak'a kaymaz")
        void istanbulMidnightTransactionsNotShifted() {
            // 10 Ocak 00:30 Istanbul = 2026-01-09T21:30:00Z
            Instant midnightIstanbul = Instant.parse("2026-01-09T21:30:00Z");

            LocalDate reportDate = LocalDate.of(2026, Month.JANUARY, 10);
            InstantRange range = helper.convertToUtcRange(reportDate, reportDate, "Europe/Istanbul");

            // Bu UTC anı range içinde olmalı — aksi hâlde gün kayması var demektir
            assertThat(midnightIstanbul).isBetween(range.start(), range.end());
        }

        @Test
        @DisplayName("Istanbul: 9 Ocak 23:59 Istanbul işlemi 10 Ocak raporuna girmemeli")
        void istanbulPreviousDayNotIncluded() {
            // 9 Ocak 23:59:59 Istanbul = 2026-01-09T20:59:59Z
            Instant endOfPrevDay = Instant.parse("2026-01-09T20:59:59Z");

            LocalDate reportDate = LocalDate.of(2026, Month.JANUARY, 10);
            InstantRange range = helper.convertToUtcRange(reportDate, reportDate, "Europe/Istanbul");

            assertThat(endOfPrevDay).isBefore(range.start());
        }
    }

    // =========================================================================
    // DST geçiş günleri
    // =========================================================================

    @Nested
    @DisplayName("DST geçiş günleri")
    class DstTransitions {

        @Test
        @DisplayName("America/New_York — ileri saat günü (2026-03-08): gün 23 saat, start doğru hesaplanır")
        void newYorkSpringForwardDay() {
            // 2026-03-08: saat 02:00 → 03:00'e atlar, bu gün 23 saat sürer
            LocalDate springForward = LocalDate.of(2026, Month.MARCH, 8);

            InstantRange range = helper.convertToUtcRange(springForward, springForward, "America/New_York");

            // Gün başlangıcı: 2026-03-08T00:00 EST (UTC-5) = 2026-03-08T05:00Z
            assertThat(range.start()).isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
            // Gün bitişi: 2026-03-08T23:59:59.999 EDT (UTC-4) = 2026-03-09T03:59:59.999Z
            assertThat(range.end()).isEqualTo(Instant.parse("2026-03-09T03:59:59.999999999Z"));
        }

        @Test
        @DisplayName("America/New_York — geri saat günü (2026-11-01): gün 25 saat, aralık tutarlı")
        void newYorkFallBackDay() {
            // 2026-11-01: saat 02:00 → 01:00'e döner, bu gün 25 saat sürer
            LocalDate fallBack = LocalDate.of(2026, Month.NOVEMBER, 1);

            InstantRange range = helper.convertToUtcRange(fallBack, fallBack, "America/New_York");

            // Gün başlangıcı: 2026-11-01T00:00 EDT (UTC-4) = 2026-11-01T04:00Z
            assertThat(range.start()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
            // Gün bitişi: 2026-11-01T23:59:59.999 EST (UTC-5) = 2026-11-02T04:59:59.999Z
            assertThat(range.end()).isEqualTo(Instant.parse("2026-11-02T04:59:59.999999999Z"));
        }
    }

    // =========================================================================
    // Parametrized çoklu timezone
    // =========================================================================

    @ParameterizedTest(name = "2026-06-01 @ {0} → start={1}")
    @CsvSource({
            "Asia/Dubai,          2026-05-31T20:00:00Z",   // UTC+4
            "Asia/Tokyo,          2026-05-31T15:00:00Z",   // UTC+9
            "Europe/Paris,        2026-05-31T22:00:00Z",   // UTC+2 (CEST)
            "America/Los_Angeles, 2026-06-01T07:00:00Z",   // UTC-7 (PDT)
    })
    @DisplayName("Çeşitli timezone'larda gün başlangıcı UTC dönüşümü")
    void multipleTimezoneStarts(String timezone, String expectedStartUtc) {
        LocalDate date = LocalDate.of(2026, Month.JUNE, 1);

        InstantRange range = helper.convertToUtcRange(date, date, timezone);

        assertThat(range.start()).isEqualTo(Instant.parse(expectedStartUtc));
    }

    // =========================================================================
    // Guard / validasyon testleri
    // =========================================================================

    @Nested
    @DisplayName("Validasyon")
    class Validation {

        @Test
        @DisplayName("null startDate → BusinessException")
        void nullStartDate() {
            assertThatThrownBy(() ->
                    helper.convertToUtcRange(null, LocalDate.now(), "UTC"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("null endDate → BusinessException")
        void nullEndDate() {
            assertThatThrownBy(() ->
                    helper.convertToUtcRange(LocalDate.now(), null, "UTC"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("endDate < startDate → BusinessException")
        void endBeforeStart() {
            LocalDate start = LocalDate.of(2026, 6, 10);
            LocalDate end   = LocalDate.of(2026, 6, 5);

            assertThatThrownBy(() ->
                    helper.convertToUtcRange(start, end, "UTC"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("önce olamaz");
        }

        @Test
        @DisplayName("Geçersiz timezone → BusinessException")
        void invalidTimezone() {
            assertThatThrownBy(() ->
                    helper.convertToUtcRange(LocalDate.now(), LocalDate.now(), "Mars/Olympus"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Geçersiz IANA timezone");
        }

        @Test
        @DisplayName("InstantRange start > end olursa IllegalArgumentException")
        void instantRangeInvariant() {
            Instant later  = Instant.parse("2026-01-10T12:00:00Z");
            Instant earlier = Instant.parse("2026-01-10T08:00:00Z");

            assertThatThrownBy(() -> new InstantRange(later, earlier))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sonra olamaz");
        }
    }
}
