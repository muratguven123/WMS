package com.wms.core.service;

import com.wms.core.dto.address.CityDto;
import com.wms.core.dto.geo.*;
import com.wms.core.entity.City;
import com.wms.core.entity.Country;
import com.wms.core.entity.StateProvince;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.integration.LocalizationUsageClient;
import com.wms.core.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeoAdminService")
class GeoAdminServiceTest {

    @Mock private CountryRepository countryRepository;
    @Mock private StateProvinceRepository stateProvinceRepository;
    @Mock private CityRepository cityRepository;
    @Mock private DistrictRepository districtRepository;
    @Mock private NeighborhoodRepository neighborhoodRepository;
    @Mock private LocalizationUsageClient localizationUsageClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GeoAdminService geoAdminService;

    private static final Long COUNTRY_TR = 1L;
    private static final Long COUNTRY_US = 10L;
    private static final Long STATE_ID   = 20L;
    private static final Long CITY_ID    = 30L;

    private Country country(Long id, String iso, String name) {
        Country c = Country.builder().isoCode(iso).name(name).build();
        c.setId(id);
        return c;
    }

    private StateProvince state(Long id, Country c, String name, String code) {
        StateProvince s = StateProvince.builder().country(c).name(name).code(code).build();
        s.setId(id);
        return s;
    }

    // ══════════════════════════════════════════════════════════════════
    // Ülke
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createCountry")
    class CreateCountry {

        @Test
        @DisplayName("isoCode uppercase'e normalize edilir ve kayıt oluşturulur")
        void createsAndNormalizesIso() {
            when(countryRepository.existsByIsoCodeIncludingInactive("DEU")).thenReturn(false);
            when(countryRepository.save(any(Country.class))).thenAnswer(inv -> {
                Country c = inv.getArgument(0);
                c.setId(99L);
                return c;
            });

            CountryAdminDto dto = geoAdminService.createCountry(
                    new CreateCountryRequest("deu", "  Almanya  "));

            assertThat(dto.isoCode()).isEqualTo("DEU");
            assertThat(dto.name()).isEqualTo("Almanya");
            assertThat(dto.active()).isTrue();
            verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
        }

        @Test
        @DisplayName("pasif kayıt dahil mükerrer isoCode → 409 GEO_ISO_EXISTS")
        void rejectsDuplicateIso() {
            when(countryRepository.existsByIsoCodeIncludingInactive("TUR")).thenReturn(true);

            assertThatThrownBy(() -> geoAdminService.createCountry(
                    new CreateCountryRequest("tur", "Türkiye")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_ISO_EXISTS"));
            verify(countryRepository, never()).save(any());
        }

        @Test
        @DisplayName("geçersiz iso formatı → 400 GEO_ISO_INVALID")
        void rejectsInvalidIso() {
            assertThatThrownBy(() -> geoAdminService.createCountry(
                    new CreateCountryRequest("T1", "Test")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_ISO_INVALID"));
        }
    }

    @Nested
    @DisplayName("updateCountry / deactivate / reactivate")
    class CountryLifecycle {

        @Test
        @DisplayName("ad değişince kaydedilir ve audit yayınlanır")
        void updatesName() {
            Country tr = country(COUNTRY_TR, "TUR", "Turkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));

            CountryAdminDto dto = geoAdminService.updateCountry(
                    COUNTRY_TR, new UpdateCountryRequest("Türkiye"));

            assertThat(dto.name()).isEqualTo("Türkiye");
            verify(countryRepository).save(tr);
            verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
        }

        @Test
        @DisplayName("ad aynıysa kayıt ve audit yapılmaz")
        void skipsNoopUpdate() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));

            geoAdminService.updateCountry(COUNTRY_TR, new UpdateCountryRequest("Türkiye"));

            verify(countryRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("pasifleştirme soft delete çağırır")
        void deactivates() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));

            geoAdminService.deactivateCountry(COUNTRY_TR);

            verify(countryRepository).delete(tr);
            verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
        }

        @Test
        @DisplayName("zaten aktif ülkenin reaktivasyonu → 409")
        void rejectsReactivatingActive() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            tr.setActive(true);
            when(countryRepository.findByIdIncludingInactive(COUNTRY_TR))
                    .thenReturn(Optional.of(tr));

            assertThatThrownBy(() -> geoAdminService.reactivateCountry(COUNTRY_TR))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_COUNTRY_ALREADY_ACTIVE"));
        }

        @Test
        @DisplayName("pasif ülke reaktive edilir")
        void reactivatesInactive() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            tr.setActive(false);
            when(countryRepository.findByIdIncludingInactive(COUNTRY_TR))
                    .thenReturn(Optional.of(tr));

            CountryAdminDto dto = geoAdminService.reactivateCountry(COUNTRY_TR);

            assertThat(dto.active()).isTrue();
            verify(countryRepository).reactivate(COUNTRY_TR);
        }
    }

    @Nested
    @DisplayName("getCountryUsage")
    class Usage {

        @Test
        @DisplayName("localization erişilebilir → sayılar dolu")
        void mapsUsage() {
            when(countryRepository.findByIdIncludingInactive(COUNTRY_TR))
                    .thenReturn(Optional.of(country(COUNTRY_TR, "TUR", "Türkiye")));
            when(localizationUsageClient.fetchUsage(COUNTRY_TR))
                    .thenReturn(Optional.of(new LocalizationUsageClient.CountryUsage(42, true)));

            CountryUsageDto dto = geoAdminService.getCountryUsage(COUNTRY_TR);

            assertThat(dto.addressCount()).isEqualTo(42);
            assertThat(dto.templateExists()).isTrue();
            assertThat(dto.checkAvailable()).isTrue();
        }

        @Test
        @DisplayName("localization erişilemez → checkAvailable=false, sayılar null (best-effort)")
        void degradesGracefully() {
            when(countryRepository.findByIdIncludingInactive(COUNTRY_TR))
                    .thenReturn(Optional.of(country(COUNTRY_TR, "TUR", "Türkiye")));
            when(localizationUsageClient.fetchUsage(COUNTRY_TR)).thenReturn(Optional.empty());

            CountryUsageDto dto = geoAdminService.getCountryUsage(COUNTRY_TR);

            assertThat(dto.addressCount()).isNull();
            assertThat(dto.templateExists()).isNull();
            assertThat(dto.checkAvailable()).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Eyalet
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addState / deactivateState")
    class States {

        @Test
        @DisplayName("eyalet eklenir, code uppercase'e normalize edilir")
        void addsState() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));
            when(stateProvinceRepository.save(any(StateProvince.class))).thenAnswer(inv -> {
                StateProvince s = inv.getArgument(0);
                s.setId(STATE_ID);
                return s;
            });

            var dto = geoAdminService.addState(COUNTRY_TR, new UpsertStateRequest("Marmara", "mr"));

            assertThat(dto.name()).isEqualTo("Marmara");
            assertThat(dto.code()).isEqualTo("MR");
            verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
        }

        @Test
        @DisplayName("aynı kodlu eyalet → 409 GEO_STATE_CODE_EXISTS")
        void rejectsDuplicateCode() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));
            when(stateProvinceRepository.findByCountryIdAndCode(COUNTRY_TR, "MR"))
                    .thenReturn(Optional.of(state(99L, tr, "Marmara", "MR")));

            assertThatThrownBy(() -> geoAdminService.addState(
                    COUNTRY_TR, new UpsertStateRequest("Marmara 2", "mr")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_STATE_CODE_EXISTS"));
        }

        @Test
        @DisplayName("aynı adlı eyalet (case-insensitive) → 409 GEO_STATE_NAME_EXISTS")
        void rejectsDuplicateName() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));
            when(stateProvinceRepository.findByCountryIdOrderByNameAsc(COUNTRY_TR))
                    .thenReturn(List.of(state(99L, tr, "Marmara", "MR")));

            assertThatThrownBy(() -> geoAdminService.addState(
                    COUNTRY_TR, new UpsertStateRequest("MARMARA", null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_STATE_NAME_EXISTS"));
        }

        @Test
        @DisplayName("aktif şehri olan eyalet pasifleştirilemez → 409 GEO_STATE_HAS_CITIES")
        void rejectsDeactivatingStateWithCities() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(stateProvinceRepository.findById(STATE_ID))
                    .thenReturn(Optional.of(state(STATE_ID, tr, "Marmara", "MR")));
            when(cityRepository.existsByStateProvinceId(STATE_ID)).thenReturn(true);

            assertThatThrownBy(() -> geoAdminService.deactivateState(STATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_STATE_HAS_CITIES"));
            verify(stateProvinceRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("importStates (CSV)")
    class ImportStates {

        @Test
        @DisplayName("başlık atlanır, mükerrerler raporlanır, geçerliler kaydedilir")
        void importsCsv() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));
            when(stateProvinceRepository.existsByCountryIdAndName(COUNTRY_TR, "İstanbul"))
                    .thenReturn(false);
            when(stateProvinceRepository.existsByCountryIdAndName(COUNTRY_TR, "Ankara"))
                    .thenReturn(true); // DB'de zaten var
            when(stateProvinceRepository.existsByCountryIdAndName(COUNTRY_TR, "İzmir"))
                    .thenReturn(false);
            when(stateProvinceRepository.findByCountryIdAndCode(COUNTRY_TR, "34"))
                    .thenReturn(Optional.empty());
            when(stateProvinceRepository.findByCountryIdAndCode(COUNTRY_TR, "35"))
                    .thenReturn(Optional.empty());

            String csv = """
                    name,code
                    İstanbul,34
                    Ankara,06
                    İzmir,35
                    İstanbul,34
                    """;

            StateImportResultDto result = geoAdminService.importStates(COUNTRY_TR, csv);

            assertThat(result.imported()).isEqualTo(2);  // İstanbul, İzmir
            assertThat(result.skipped()).isEqualTo(2);   // Ankara (DB), İstanbul (dosya içi)
            assertThat(result.errors()).hasSize(2);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<StateProvince>> captor =
                    ArgumentCaptor.forClass((Class<List<StateProvince>>) (Class<?>) List.class);
            verify(stateProvinceRepository).saveAll(captor.capture());
            assertThat(captor.getValue())
                    .extracting(StateProvince::getName)
                    .containsExactly("İstanbul", "İzmir");
        }

        @Test
        @DisplayName("boş içerik → 400 GEO_CSV_EMPTY")
        void rejectsEmptyCsv() {
            when(countryRepository.findById(COUNTRY_TR))
                    .thenReturn(Optional.of(country(COUNTRY_TR, "TUR", "Türkiye")));

            assertThatThrownBy(() -> geoAdminService.importStates(COUNTRY_TR, "  "))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_CSV_EMPTY"));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Şehir
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addCity / assignCityToState")
    class Cities {

        @Test
        @DisplayName("eyaletsiz şehir doğrudan ülkeye bağlanır")
        void addsStatelessCity() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));
            when(cityRepository.existsByCountryIdAndStateProvinceIsNullAndName(COUNTRY_TR, "İzmir"))
                    .thenReturn(false);
            when(cityRepository.save(any(City.class))).thenAnswer(inv -> {
                City c = inv.getArgument(0);
                c.setId(CITY_ID);
                return c;
            });

            CityDto dto = geoAdminService.addCity(COUNTRY_TR, new CreateCityRequest("İzmir", null));

            assertThat(dto.name()).isEqualTo("İzmir");
        }

        @Test
        @DisplayName("başka ülkenin eyaletine şehir eklenemez → GEO_STATE_COUNTRY_MISMATCH")
        void rejectsCrossCountryState() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            Country us = country(COUNTRY_US, "USA", "ABD");
            when(countryRepository.findById(COUNTRY_TR)).thenReturn(Optional.of(tr));
            when(stateProvinceRepository.findById(STATE_ID))
                    .thenReturn(Optional.of(state(STATE_ID, us, "California", "CA")));

            assertThatThrownBy(() -> geoAdminService.addCity(
                    COUNTRY_TR, new CreateCityRequest("İzmir", STATE_ID)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_STATE_COUNTRY_MISMATCH"));
        }

        @Test
        @DisplayName("şehir kendi ülkesindeki eyalete bağlanır (TR geçiş senaryosu)")
        void assignsCityToState() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            StateProvince marmara = state(STATE_ID, tr, "Marmara", "MR");
            City izmir = City.builder().country(tr).name("İzmir").build();
            izmir.setId(CITY_ID);

            when(cityRepository.findById(CITY_ID)).thenReturn(Optional.of(izmir));
            when(stateProvinceRepository.findById(STATE_ID)).thenReturn(Optional.of(marmara));
            when(cityRepository.existsByStateProvinceIdAndName(STATE_ID, "İzmir")).thenReturn(false);

            geoAdminService.assignCityToState(CITY_ID, new AssignStateRequest(STATE_ID));

            assertThat(izmir.getStateProvince()).isEqualTo(marmara);
            verify(cityRepository).save(izmir);
            verify(eventPublisher).publishEvent(any(ConfigChangeEvent.class));
        }

        @Test
        @DisplayName("şehir başka ülkenin eyaletine bağlanamaz")
        void rejectsAssigningToForeignState() {
            Country tr = country(COUNTRY_TR, "TUR", "Türkiye");
            Country us = country(COUNTRY_US, "USA", "ABD");
            City izmir = City.builder().country(tr).name("İzmir").build();
            izmir.setId(CITY_ID);

            when(cityRepository.findById(CITY_ID)).thenReturn(Optional.of(izmir));
            when(stateProvinceRepository.findById(STATE_ID))
                    .thenReturn(Optional.of(state(STATE_ID, us, "California", "CA")));

            assertThatThrownBy(() -> geoAdminService.assignCityToState(
                    CITY_ID, new AssignStateRequest(STATE_ID)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo("GEO_STATE_COUNTRY_MISMATCH"));
        }
    }
}
