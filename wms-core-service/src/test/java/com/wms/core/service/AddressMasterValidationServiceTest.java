package com.wms.core.service;

import com.wms.core.entity.City;
import com.wms.core.entity.Country;
import com.wms.core.entity.District;
import com.wms.core.entity.Neighborhood;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.CityRepository;
import com.wms.core.repository.DistrictRepository;
import com.wms.core.repository.NeighborhoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * AddressMasterValidationService için birim testleri.
 *
 * Test senaryoları:
 * - Happy path: tüm hiyerarşi tutarlı, exception yok
 * - neighborhoodId null: mahalle katmanı atlanır, doğrulama geçer
 * - Neighborhood → District uyuşmazlığı
 * - Neighborhood bulunamadı (pasif kayıt dahil)
 * - District → City uyuşmazlığı
 * - District bulunamadı
 * - City → Country uyuşmazlığı
 * - City bulunamadı
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddressMasterValidationService — hiyerarşi doğrulama testleri")
class AddressMasterValidationServiceTest {

    @Mock private NeighborhoodRepository neighborhoodRepository;
    @Mock private DistrictRepository     districtRepository;
    @Mock private CityRepository         cityRepository;

    @InjectMocks
    private AddressMasterValidationService validationService;

    // ----------------------------------------------------------------
    // Test fixture Long'leri
    // ----------------------------------------------------------------
    private Long countryId;
    private Long cityId;
    private Long districtId;
    private Long neighborhoodId;

    // Yanlış ilişki kurmak için kullanılan "yabancı" Long'ler
    private Long otherCountryId;
    private Long otherCityId;
    private Long otherDistrictId;

    @BeforeEach
    void setUp() {
        countryId      = 1L;
        cityId         = 2L;
        districtId     = 3L;
        neighborhoodId = 4L;

        otherCountryId  = 10L;
        otherCityId     = 11L;
        otherDistrictId = 12L;
    }

    // ================================================================
    // Helper — tutarlı hiyerarşi nesneleri oluşturur
    // ================================================================

    private Country buildCountry(Long id) {
        Country c = new Country();
        c.setId(id);
        return c;
    }

    private City buildCity(Long id, Long forCountryId) {
        City city = new City();
        city.setId(id);
        city.setCountry(buildCountry(forCountryId));
        return city;
    }

    private District buildDistrict(Long id, Long forCityId) {
        District d = new District();
        d.setId(id);
        d.setCity(buildCity(forCityId, countryId));
        return d;
    }

    private Neighborhood buildNeighborhood(Long id, Long forDistrictId) {
        Neighborhood n = new Neighborhood();
        n.setId(id);
        n.setDistrict(buildDistrict(forDistrictId, cityId));
        return n;
    }

    /** Tüm repository mock'larını tutarlı hiyerarşiyle yapılandırır. */
    private void mockFullHierarchyConsistent() {
        when(neighborhoodRepository.findById(neighborhoodId))
                .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, districtId)));
        when(districtRepository.findById(districtId))
                .thenReturn(Optional.of(buildDistrict(districtId, cityId)));
        when(cityRepository.findById(cityId))
                .thenReturn(Optional.of(buildCity(cityId, countryId)));
    }

    // ================================================================
    // 1. Happy path
    // ================================================================

    @Nested
    @DisplayName("1. Happy path")
    class HappyPath {

        @Test
        @DisplayName("Tam hiyerarşi tutarlı — exception fırlatılmamalı")
        void fullHierarchyConsistent_shouldNotThrow() {
            mockFullHierarchyConsistent();

            assertThatCode(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("neighborhoodId null — mahalle katmanı atlanır, doğrulama geçmeli")
        void neighborhoodIdNull_shouldSkipNeighborhoodCheck() {
            when(districtRepository.findById(districtId))
                    .thenReturn(Optional.of(buildDistrict(districtId, cityId)));
            when(cityRepository.findById(cityId))
                    .thenReturn(Optional.of(buildCity(cityId, countryId)));

            assertThatCode(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, null))
                    .doesNotThrowAnyException();
        }
    }

    // ================================================================
    // 2. Neighborhood katmanı hataları
    // ================================================================

    @Nested
    @DisplayName("2. Neighborhood katmanı hataları")
    class NeighborhoodErrors {

        @Test
        @DisplayName("Neighborhood bulunamadı — NOT_FOUND exception")
        void neighborhoodNotFound_shouldThrow() {
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be).hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
                        assertThat(be.getErrorCode()).isEqualTo("ADDRESS_NEIGHBORHOOD_NOT_FOUND");
                    });
        }

        @Test
        @DisplayName("Neighborhood farklı district'e bağlı — MISMATCH exception")
        void neighborhoodBelongsToDifferentDistrict_shouldThrow() {
            // Neighborhood, otherDistrictId'ye bağlı ama istek districtId gönderiyor
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, otherDistrictId)));

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo("ADDRESS_NEIGHBORHOOD_DISTRICT_MISMATCH");
                        assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }
    }

    // ================================================================
    // 3. District katmanı hataları
    // ================================================================

    @Nested
    @DisplayName("3. District katmanı hataları")
    class DistrictErrors {

        @Test
        @DisplayName("District bulunamadı — NOT_FOUND exception")
        void districtNotFound_shouldThrow() {
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, districtId)));
            when(districtRepository.findById(districtId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex ->
                            assertThat(((BusinessException) ex).getErrorCode())
                                    .isEqualTo("ADDRESS_DISTRICT_NOT_FOUND"));
        }

        @Test
        @DisplayName("District farklı city'e bağlı — MISMATCH exception")
        void districtBelongsToDifferentCity_shouldThrow() {
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, districtId)));

            // District, otherCityId'ye bağlı ama istek cityId gönderiyor
            District d = new District();
            d.setId(districtId);
            d.setCity(buildCity(otherCityId, countryId));
            when(districtRepository.findById(districtId)).thenReturn(Optional.of(d));

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo("ADDRESS_DISTRICT_CITY_MISMATCH");
                        assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }
    }

    // ================================================================
    // 4. City katmanı hataları
    // ================================================================

    @Nested
    @DisplayName("4. City katmanı hataları")
    class CityErrors {

        @Test
        @DisplayName("City bulunamadı — NOT_FOUND exception")
        void cityNotFound_shouldThrow() {
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, districtId)));
            when(districtRepository.findById(districtId))
                    .thenReturn(Optional.of(buildDistrict(districtId, cityId)));
            when(cityRepository.findById(cityId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex ->
                            assertThat(((BusinessException) ex).getErrorCode())
                                    .isEqualTo("ADDRESS_CITY_NOT_FOUND"));
        }

        @Test
        @DisplayName("City farklı country'e bağlı — MISMATCH exception")
        void cityBelongsToDifferentCountry_shouldThrow() {
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, districtId)));
            when(districtRepository.findById(districtId))
                    .thenReturn(Optional.of(buildDistrict(districtId, cityId)));
            // City, otherCountryId'ye bağlı ama istek countryId gönderiyor
            when(cityRepository.findById(cityId))
                    .thenReturn(Optional.of(buildCity(cityId, otherCountryId)));

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo("ADDRESS_CITY_COUNTRY_MISMATCH");
                        assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }
    }

    // ================================================================
    // 5. Pasif kayıt senaryosu
    // ================================================================

    @Nested
    @DisplayName("5. Pasif kayıt senaryosu")
    class InactiveRecord {

        @Test
        @DisplayName("Pasif district — @SQLRestriction nedeniyle findById boş döner, NOT_FOUND exception")
        void inactiveDistrict_treatedAsNotFound() {
            // @SQLRestriction("is_active = true") pasif kaydı filtreler;
            // findById Optional.empty() döner → NOT_FOUND ile aynı davranış
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, districtId)));
            when(districtRepository.findById(districtId))
                    .thenReturn(Optional.empty()); // pasif kayıt → filtreden düştü

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex ->
                            assertThat(((BusinessException) ex).getErrorCode())
                                    .isEqualTo("ADDRESS_DISTRICT_NOT_FOUND"));
        }

        @Test
        @DisplayName("Pasif city — NOT_FOUND exception")
        void inactiveCity_treatedAsNotFound() {
            when(neighborhoodRepository.findById(neighborhoodId))
                    .thenReturn(Optional.of(buildNeighborhood(neighborhoodId, districtId)));
            when(districtRepository.findById(districtId))
                    .thenReturn(Optional.of(buildDistrict(districtId, cityId)));
            when(cityRepository.findById(cityId))
                    .thenReturn(Optional.empty()); // pasif kayıt

            assertThatThrownBy(() ->
                    validationService.validateAddressHierarchy(
                            countryId, cityId, districtId, neighborhoodId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex ->
                            assertThat(((BusinessException) ex).getErrorCode())
                                    .isEqualTo("ADDRESS_CITY_NOT_FOUND"));
        }
    }

    // ================================================================
    // AssertJ shortcut (import static yerine inline kullanım)
    // ================================================================

    private static <T> org.assertj.core.api.AbstractObjectAssert<?, T> assertThat(T actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
