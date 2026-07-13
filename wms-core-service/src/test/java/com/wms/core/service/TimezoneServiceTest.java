package com.wms.core.service;

import com.wms.core.entity.Location;
import com.wms.core.entity.User;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TimezoneService birim testleri.
 *
 * DST (Daylight Saving Time) geçiş senaryoları özellikle test edilir:
 * - Europe/Istanbul: Türkiye 2016'dan itibaren sabit UTC+3 kullanır (DST yok).
 * - America/New_York: İleri/geri saat uygulaması aktif (EDT/EST).
 * - Europe/London: GMT/BST geçişleri.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TimezoneService")
class TimezoneServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private TimezoneService timezoneService;

    private Long userId;
    private Long locationId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        locationId = 1L;
    }

    // =========================================================================
    // convertToLocalTime(Instant, String)
    // =========================================================================

    @Nested
    @DisplayName("convertToLocalTime")
    class ConvertToLocalTime {

        @Test
        @DisplayName("UTC Instant'ı Europe/Istanbul'a doğru çevirir (UTC+3, DST yok)")
        void convertsToIstanbul() {
            // 2024-06-15 10:00:00 UTC → Istanbul UTC+3 → 13:00:00 lokal
            Instant utc = Instant.parse("2024-06-15T10:00:00Z");

            LocalDateTime result = timezoneService.convertToLocalTime(utc, "Europe/Istanbul");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, Month.JUNE, 15, 13, 0, 0));
        }

        @Test
        @DisplayName("UTC Instant'ı America/New_York'a (EDT, UTC-4) doğru çevirir")
        void convertsToNewYorkSummer() {
            // 2024-07-04 15:00:00 UTC → New York EDT (UTC-4) → 11:00:00 lokal
            Instant utc = Instant.parse("2024-07-04T15:00:00Z");

            LocalDateTime result = timezoneService.convertToLocalTime(utc, "America/New_York");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, Month.JULY, 4, 11, 0, 0));
        }

        @Test
        @DisplayName("UTC Instant'ı America/New_York'a (EST, UTC-5) doğru çevirir")
        void convertsToNewYorkWinter() {
            // 2024-01-15 15:00:00 UTC → New York EST (UTC-5) → 10:00:00 lokal
            Instant utc = Instant.parse("2024-01-15T15:00:00Z");

            LocalDateTime result = timezoneService.convertToLocalTime(utc, "America/New_York");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, Month.JANUARY, 15, 10, 0, 0));
        }

        @Test
        @DisplayName("null Instant fırlatır BusinessException")
        void throwsOnNullInstant() {
            assertThatThrownBy(() -> timezoneService.convertToLocalTime(null, "Europe/Istanbul"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("null olamaz");
        }

        @Test
        @DisplayName("Geçersiz timezone string fırlatır BusinessException")
        void throwsOnInvalidTimezone() {
            Instant utc = Instant.parse("2024-06-15T10:00:00Z");

            assertThatThrownBy(() -> timezoneService.convertToLocalTime(utc, "Mars/Olympus"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Geçersiz IANA timezone");
        }

        @Test
        @DisplayName("Boş timezone string fırlatır BusinessException")
        void throwsOnBlankTimezone() {
            Instant utc = Instant.parse("2024-06-15T10:00:00Z");

            assertThatThrownBy(() -> timezoneService.convertToLocalTime(utc, "  "))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("boş olamaz");
        }
    }

    // =========================================================================
    // DST Geçiş Senaryoları
    // =========================================================================

    @Nested
    @DisplayName("DST (Daylight Saving Time) Geçiş Testleri")
    class DstTransitionTests {

        @Test
        @DisplayName("America/New_York — ileri saat geçişi (Mart 2024): 02:00 → 03:00 atlar")
        void newYorkSpringForwardTransition() {
            // 2024-03-10 06:59:59 UTC → New York EST (UTC-5) → 01:59:59 lokal
            Instant beforeTransition = Instant.parse("2024-03-10T06:59:59Z");
            LocalDateTime result1 = timezoneService.convertToLocalTime(beforeTransition, "America/New_York");
            assertThat(result1).isEqualTo(LocalDateTime.of(2024, Month.MARCH, 10, 1, 59, 59));

            // 2024-03-10 07:00:00 UTC → New York EDT (UTC-4) → 03:00:00 lokal (02:00 atlandı)
            Instant afterTransition = Instant.parse("2024-03-10T07:00:00Z");
            LocalDateTime result2 = timezoneService.convertToLocalTime(afterTransition, "America/New_York");
            assertThat(result2).isEqualTo(LocalDateTime.of(2024, Month.MARCH, 10, 3, 0, 0));
        }

        @Test
        @DisplayName("America/New_York — geri saat geçişi (Kasım 2024): 02:00 → 01:00'e döner")
        void newYorkFallBackTransition() {
            // 2024-11-03 05:59:59 UTC → New York EDT (UTC-4) → 01:59:59 lokal (son EDT anı)
            Instant lastEdt = Instant.parse("2024-11-03T05:59:59Z");
            LocalDateTime result1 = timezoneService.convertToLocalTime(lastEdt, "America/New_York");
            assertThat(result1).isEqualTo(LocalDateTime.of(2024, Month.NOVEMBER, 3, 1, 59, 59));

            // 2024-11-03 06:00:00 UTC → New York EST (UTC-5) → 01:00:00 lokal (saat geri sarıldı)
            Instant firstEst = Instant.parse("2024-11-03T06:00:00Z");
            LocalDateTime result2 = timezoneService.convertToLocalTime(firstEst, "America/New_York");
            assertThat(result2).isEqualTo(LocalDateTime.of(2024, Month.NOVEMBER, 3, 1, 0, 0));
        }

        @Test
        @DisplayName("Europe/London — BST'ye geçiş (Mart 2024): 01:00 GMT → 02:00 BST")
        void londonSpringForwardTransition() {
            // 2024-03-31 00:59:59 UTC → London GMT (UTC+0) → 00:59:59 lokal
            Instant beforeTransition = Instant.parse("2024-03-31T00:59:59Z");
            LocalDateTime result1 = timezoneService.convertToLocalTime(beforeTransition, "Europe/London");
            assertThat(result1).isEqualTo(LocalDateTime.of(2024, Month.MARCH, 31, 0, 59, 59));

            // 2024-03-31 01:00:00 UTC → London BST (UTC+1) → 02:00:00 lokal
            Instant afterTransition = Instant.parse("2024-03-31T01:00:00Z");
            LocalDateTime result2 = timezoneService.convertToLocalTime(afterTransition, "Europe/London");
            assertThat(result2).isEqualTo(LocalDateTime.of(2024, Month.MARCH, 31, 2, 0, 0));
        }

        @Test
        @DisplayName("Europe/London — GMT'ye geri dönüş (Ekim 2024): 02:00 BST → 01:00 GMT")
        void londonFallBackTransition() {
            // 2024-10-27 00:59:59 UTC → London BST (UTC+1) → 01:59:59 lokal
            Instant lastBst = Instant.parse("2024-10-27T00:59:59Z");
            LocalDateTime result1 = timezoneService.convertToLocalTime(lastBst, "Europe/London");
            assertThat(result1).isEqualTo(LocalDateTime.of(2024, Month.OCTOBER, 27, 1, 59, 59));

            // 2024-10-27 01:00:00 UTC → London GMT (UTC+0) → 01:00:00 lokal (saat geri sarıldı)
            Instant firstGmt = Instant.parse("2024-10-27T01:00:00Z");
            LocalDateTime result2 = timezoneService.convertToLocalTime(firstGmt, "Europe/London");
            assertThat(result2).isEqualTo(LocalDateTime.of(2024, Month.OCTOBER, 27, 1, 0, 0));
        }

        @Test
        @DisplayName("Europe/Istanbul sabit UTC+3 — DST uygulamaz (2016 sonrası)")
        void istanbulNoDst() {
            // Yaz
            Instant summer = Instant.parse("2024-07-15T12:00:00Z");
            // Kış
            Instant winter = Instant.parse("2024-01-15T12:00:00Z");

            LocalDateTime summerLocal = timezoneService.convertToLocalTime(summer, "Europe/Istanbul");
            LocalDateTime winterLocal = timezoneService.convertToLocalTime(winter, "Europe/Istanbul");

            // Her iki durumda da +3 saat farkı sabit kalır
            assertThat(summerLocal).isEqualTo(LocalDateTime.of(2024, Month.JULY, 15, 15, 0, 0));
            assertThat(winterLocal).isEqualTo(LocalDateTime.of(2024, Month.JANUARY, 15, 15, 0, 0));
        }

        @Test
        @DisplayName("Europe/Istanbul — AB yaz saati geçiş gününde (30 Mart 2025) +3 sabit kalır")
        void istanbulStaysPlusThreeOnEuSpringForwardDay() {
            // 2025-03-30 01:00 UTC: Avrupa'nın DST'ye geçtiği an.
            // Türkiye 2016'dan beri sabit UTC+3 — dönüşüm 04:00 olmalı, 03:00 veya 05:00 değil.
            Instant utc = Instant.parse("2025-03-30T01:00:00Z");

            LocalDateTime result = timezoneService.convertToLocalTime(utc, "Europe/Istanbul");

            assertThat(result).isEqualTo(LocalDateTime.of(2025, Month.MARCH, 30, 4, 0, 0));
        }

        @Test
        @DisplayName("Europe/Istanbul — AB kış saati geçiş gününde (27 Ekim 2024) +3 sabit kalır")
        void istanbulStaysPlusThreeOnEuFallBackDay() {
            // 2024-10-27 01:00 UTC: Avrupa'nın kış saatine döndüğü an.
            // Istanbul DST uygulamadığından +3 offset değişmez → 04:00.
            Instant utc = Instant.parse("2024-10-27T01:00:00Z");

            LocalDateTime result = timezoneService.convertToLocalTime(utc, "Europe/Istanbul");

            assertThat(result).isEqualTo(LocalDateTime.of(2024, Month.OCTOBER, 27, 4, 0, 0));
        }

        @ParameterizedTest(name = "{0} UTC → {1} timezone → {2} lokal")
        @CsvSource({
                "2024-06-01T00:00:00Z, Asia/Dubai,       2024-06-01T04:00:00",
                "2024-06-01T00:00:00Z, Asia/Tokyo,       2024-06-01T09:00:00",
                "2024-06-01T00:00:00Z, America/Los_Angeles, 2024-05-31T17:00:00",
                "2024-12-01T00:00:00Z, America/Los_Angeles, 2024-11-30T16:00:00"
        })
        @DisplayName("Çeşitli timezone'larda UTC dönüşümü")
        void multipleTimezones(String utcStr, String timezone, String expectedLocalStr) {
            Instant utc = Instant.parse(utcStr);
            LocalDateTime expected = LocalDateTime.parse(expectedLocalStr);

            LocalDateTime result = timezoneService.convertToLocalTime(utc, timezone);

            assertThat(result).isEqualTo(expected);
        }
    }

    // =========================================================================
    // convertToLocalTime(Instant, Long, Long) — bağlamsal overload (uçtan uca)
    // =========================================================================

    @Nested
    @DisplayName("convertToLocalTime (userId + locationId overload)")
    class ConvertToLocalTimeWithContext {

        @Test
        @DisplayName("Kullanıcı tercihi (Europe/Istanbul) lokasyon timezone'undan bağımsız uygulanır")
        void userPreferenceWinsOverLocationTimezone() {
            // Kullanıcı Istanbul (+3) tercih etmiş; lokasyon US/Eastern olsa bile
            // sonuç +3 olmalı. Lokasyon stub'lanmıyor — repository'ye hiç gidilmemeli.
            User user = mockUser("Europe/Istanbul");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            Instant utc = Instant.parse("2024-06-15T10:00:00Z");
            LocalDateTime result = timezoneService.convertToLocalTime(utc, userId, locationId);

            // US/Eastern olsaydı 06:00 (EDT) dönerdi — +3 ile 13:00 bekleniyor
            assertThat(result).isEqualTo(LocalDateTime.of(2024, Month.JUNE, 15, 13, 0, 0));
            verifyNoInteractions(locationRepository);
        }

        @Test
        @DisplayName("Kullanıcı tercihi null ise Location.timezone (Europe/Istanbul) ile dönüştürür")
        void fallsBackToLocationTimezoneEndToEnd() {
            User user = mockUser(null);
            Location location = mockLocation("Europe/Istanbul");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));

            Instant utc = Instant.parse("2024-06-15T10:00:00Z");
            LocalDateTime result = timezoneService.convertToLocalTime(utc, userId, locationId);

            assertThat(result).isEqualTo(LocalDateTime.of(2024, Month.JUNE, 15, 13, 0, 0));
            verify(locationRepository).findById(locationId);
        }

        @Test
        @DisplayName("Kullanıcının geçersiz preferredTimezone'u BusinessException fırlatır")
        void throwsWhenUserPreferredTimezoneIsInvalid() {
            User user = mockUser("Europe/BilinmeyenYer");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            Instant utc = Instant.parse("2024-06-15T10:00:00Z");

            assertThatThrownBy(() -> timezoneService.convertToLocalTime(utc, userId, locationId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Geçersiz IANA timezone");
        }
    }

    // =========================================================================
    // resolveTargetTimezone
    // =========================================================================

    @Nested
    @DisplayName("resolveTargetTimezone")
    class ResolveTargetTimezone {

        @Test
        @DisplayName("Kullanıcının preferredTimezone'u varsa onu döner")
        void returnsUserPreferredTimezone() {
            User user = mockUser("America/New_York");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String result = timezoneService.resolveTargetTimezone(userId, locationId);

            assertThat(result).isEqualTo("America/New_York");
            verifyNoInteractions(locationRepository); // Lokasyona bakılmadı
        }

        @Test
        @DisplayName("preferredTimezone null ise Location.timezone'u döner")
        void fallsBackToLocationTimezoneWhenPreferredIsNull() {
            User user = mockUser(null);
            Location location = mockLocation("Asia/Dubai");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));

            String result = timezoneService.resolveTargetTimezone(userId, locationId);

            assertThat(result).isEqualTo("Asia/Dubai");
        }

        @Test
        @DisplayName("preferredTimezone boş string ise Location.timezone'u döner")
        void fallsBackToLocationTimezoneWhenPreferredIsBlank() {
            User user = mockUser("   ");
            Location location = mockLocation("Europe/Paris");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));

            String result = timezoneService.resolveTargetTimezone(userId, locationId);

            assertThat(result).isEqualTo("Europe/Paris");
        }

        @Test
        @DisplayName("Kullanıcı bulunamazsa BusinessException fırlatır")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> timezoneService.resolveTargetTimezone(userId, locationId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Kullanıcı bulunamadı");
        }

        @Test
        @DisplayName("Lokasyon bulunamazsa BusinessException fırlatır")
        void throwsWhenLocationNotFound() {
            User user = mockUser(null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(locationRepository.findById(locationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> timezoneService.resolveTargetTimezone(userId, locationId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Lokasyon bulunamadı");
        }
    }

    // =========================================================================
    // Yardımcı builder metodları
    // =========================================================================

    private User mockUser(String preferredTimezone) {
        User user = new User();
        user.setPreferredTimezone(preferredTimezone);
        return user;
    }

    private Location mockLocation(String timezone) {
        Location location = new Location();
        location.setTimezone(timezone);
        return location;
    }
}
