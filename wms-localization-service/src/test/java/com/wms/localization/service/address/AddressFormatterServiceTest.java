package com.wms.localization.service.address;

import com.wms.localization.service.address.formatter.CountryIsoCodeResolver;
import com.wms.localization.service.address.formatter.TurkeyAddressFormatterStrategy;
import com.wms.localization.service.address.formatter.UsAddressFormatterStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressFormatterService")
class AddressFormatterServiceTest {

    @Mock
    private CountryIsoCodeResolver isoCodeResolver;

    private AddressFormatterService formatterService;

    private Long countryId;

    @BeforeEach
    void setUp() {
        countryId = 1L;
        formatterService = new AddressFormatterService(
                isoCodeResolver,
                List.of(new TurkeyAddressFormatterStrategy(), new UsAddressFormatterStrategy())
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Türkiye senaryoları
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Türkiye (TR) adres formatı")
    class TurkeyFormat {

        @BeforeEach
        void givenTR() {
            given(isoCodeResolver.resolve(countryId)).willReturn("TR");
        }

        @Test
        @DisplayName("Tüm alanlar dolu — tam format üretilmeli")
        void fullAddress() {
            Map<String, Object> details = Map.of(
                    "neighborhood", "Moda",
                    "street",       "Bahariye",
                    "door_no",      "5",
                    "apartment_no", "3",
                    "district",     "Kadıköy"
            );

            String result = formatterService.generateFormattedAddress(
                    countryId, details, "İstanbul", "İstanbul", "34710");

            assertThat(result).isEqualTo(
                    "Moda Mah. Bahariye Cad. No:5 D:3, Kadıköy, İstanbul/İstanbul, 34710");
        }

        @Test
        @DisplayName("neighborhood eksik — 'Mah.' eki çıktıda olmamalı")
        void missingNeighborhood() {
            Map<String, Object> details = Map.of(
                    "street",  "İstiklal",
                    "door_no", "1"
            );

            String result = formatterService.generateFormattedAddress(
                    countryId, details, "İstanbul", "İstanbul", null);

            assertThat(result).isEqualTo("İstiklal Cad. No:1, İstanbul/İstanbul");
            assertThat(result).doesNotContain("Mah.");
        }

        @Test
        @DisplayName("door_no ve apartment_no eksik — 'No:' ve 'D:' çıktıda olmamalı")
        void missingDoorAndApartment() {
            Map<String, Object> details = Map.of(
                    "neighborhood", "Bostancı",
                    "street",       "Bağdat"
            );

            String result = formatterService.generateFormattedAddress(
                    countryId, details, "İstanbul", "İstanbul", "34744");

            assertThat(result).isEqualTo("Bostancı Mah. Bağdat Cad., İstanbul/İstanbul, 34744");
            assertThat(result).doesNotContain("No:").doesNotContain("D:");
        }

        @Test
        @DisplayName("addressDetails null — sadece city/state/zipCode birleştirilmeli")
        void nullDetails() {
            String result = formatterService.generateFormattedAddress(
                    countryId, null, "Ankara", "Ankara", "06800");

            assertThat(result).isEqualTo("Ankara/Ankara, 06800");
        }

        @Test
        @DisplayName("Tüm alanlar null — boş string dönmeli")
        void allNull() {
            String result = formatterService.generateFormattedAddress(
                    countryId, null, null, null, null);

            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ABD senaryoları
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Amerika Birleşik Devletleri (US) adres formatı")
    class UsFormat {

        @BeforeEach
        void givenUS() {
            given(isoCodeResolver.resolve(countryId)).willReturn("US");
        }

        @Test
        @DisplayName("Tüm alanlar dolu — tam format üretilmeli")
        void fullAddress() {
            Map<String, Object> details = Map.of("street", "Main");

            String result = formatterService.generateFormattedAddress(
                    countryId, details, "Los Angeles", "CA", "90001");

            assertThat(result).isEqualTo("Main St, Los Angeles, CA 90001, USA");
        }

        @Test
        @DisplayName("street eksik, po_box var — PO Box formatı kullanılmalı")
        void poBoxFallback() {
            Map<String, Object> details = Map.of("po_box", "456");

            String result = formatterService.generateFormattedAddress(
                    countryId, details, "Austin", "TX", "78701");

            assertThat(result).isEqualTo("PO Box 456, Austin, TX 78701, USA");
        }

        @Test
        @DisplayName("zipCode eksik — state sonrasında boşluk veya virgül kalıntısı olmamalı")
        void missingZipCode() {
            Map<String, Object> details = Map.of("street", "Broadway");

            String result = formatterService.generateFormattedAddress(
                    countryId, details, "New York", "NY", null);

            assertThat(result).isEqualTo("Broadway St, New York, NY, USA");
            assertThat(result).doesNotContain("  "); // çift boşluk yok
        }

        @Test
        @DisplayName("street ve po_box yoksa adres satırı çıktıda olmamalı")
        void noStreetNoPo() {
            String result = formatterService.generateFormattedAddress(
                    countryId, Map.of(), "Chicago", "IL", "60601");

            assertThat(result).isEqualTo("Chicago, IL 60601, USA");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback senaryosu
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Bilinmeyen ülke — fallback format")
    class FallbackFormat {

        @Test
        @DisplayName("Kayıtlı olmayan ISO kodu için generic birleştirme yapılmalı")
        void unknownCountry() {
            given(isoCodeResolver.resolve(countryId)).willReturn("DE");

            Map<String, Object> details = Map.of("street", "Hauptstraße");

            String result = formatterService.generateFormattedAddress(
                    countryId, details, "Berlin", "BE", "10115");

            // Tüm değerler virgülle birleştirilmiş olmalı, sıra önemli değil
            assertThat(result).contains("Hauptstraße", "Berlin", "BE", "10115");
            // Çirkin boş değer kalmamalı
            assertThat(result).doesNotContain(",,").doesNotContain(", ,");
        }
    }
}
