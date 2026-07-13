package com.wms.core.service;

import com.wms.core.dto.address.CityDto;
import com.wms.core.entity.City;
import com.wms.core.entity.Country;
import com.wms.core.entity.StateProvince;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.CityRepository;
import com.wms.core.repository.CountryRepository;
import com.wms.core.repository.DistrictRepository;
import com.wms.core.repository.NeighborhoodRepository;
import com.wms.core.repository.StateProvinceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressQueryService")
class AddressQueryServiceTest {

    @Mock private CountryRepository countryRepository;
    @Mock private StateProvinceRepository stateProvinceRepository;
    @Mock private CityRepository cityRepository;
    @Mock private DistrictRepository districtRepository;
    @Mock private NeighborhoodRepository neighborhoodRepository;

    @InjectMocks
    private AddressQueryService addressQueryService;

    private static final Long COUNTRY_TR = 1L;
    private static final Long CITY_IST  = 2L;
    private static final Long COUNTRY_US = 10L;
    private static final Long STATE_CA   = 11L;
    private static final Long CITY_SF    = 12L;

    @Test
    @DisplayName("stateId yoksa yalnızca doğrudan ülkeye bağlı şehirler döner")
    void listCities_withoutState_returnsDirectCountryCities() {
        when(countryRepository.existsById(COUNTRY_TR)).thenReturn(true);

        City istanbul = new City();
        istanbul.setId(CITY_IST);
        istanbul.setName("İstanbul");

        when(cityRepository.findByCountryIdAndStateProvinceIsNullOrderByNameAsc(COUNTRY_TR))
                .thenReturn(List.of(istanbul));

        List<CityDto> result = addressQueryService.listCities(COUNTRY_TR, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("İstanbul");
    }

    @Test
    @DisplayName("stateId verilmişse eyalete bağlı şehirler döner")
    void listCities_withState_returnsStateCities() {
        when(countryRepository.existsById(COUNTRY_US)).thenReturn(true);
        when(stateProvinceRepository.existsByIdAndCountryId(STATE_CA, COUNTRY_US)).thenReturn(true);

        City sf = new City();
        sf.setId(CITY_SF);
        sf.setName("San Francisco");

        when(cityRepository.findByStateProvinceIdOrderByNameAsc(STATE_CA))
                .thenReturn(List.of(sf));

        List<CityDto> result = addressQueryService.listCities(COUNTRY_US, STATE_CA);

        assertThat(result).extracting(CityDto::name).containsExactly("San Francisco");
    }

    @Test
    @DisplayName("stateId farklı ülkeye aitse STATE_COUNTRY_MISMATCH")
    void listCities_stateNotInCountry_throws() {
        when(countryRepository.existsById(COUNTRY_TR)).thenReturn(true);
        when(stateProvinceRepository.existsByIdAndCountryId(STATE_CA, COUNTRY_TR))
                .thenReturn(false);

        assertThatThrownBy(() -> addressQueryService.listCities(COUNTRY_TR, STATE_CA))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo("STATE_COUNTRY_MISMATCH"));
    }

    @Test
    @DisplayName("ülke bulunamazsa COUNTRY_NOT_FOUND")
    void listCities_unknownCountry_throws() {
        Long unknown = 1L;
        when(countryRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> addressQueryService.listCities(unknown, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo("COUNTRY_NOT_FOUND"));
    }
}
