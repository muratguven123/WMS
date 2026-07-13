package com.wms.localization.service;

import com.wms.localization.dto.ActiveFormatResponse;
import com.wms.localization.entity.CountryFormatConfig;
import com.wms.localization.entity.LocationFormatOverride;
import com.wms.localization.integration.LocationCountryResolver;
import com.wms.localization.repository.CountryFormatConfigRepository;
import com.wms.localization.repository.LocationFormatOverrideRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FormatConfigService")
class FormatConfigServiceTest {

    @Mock
    private LocationFormatOverrideRepository locationFormatOverrideRepository;

    @Mock
    private CountryFormatConfigRepository countryFormatConfigRepository;

    @Mock
    private LocationCountryResolver locationCountryResolver;

    @InjectMocks
    private FormatConfigService formatConfigService;

    private static final Long LOCATION_ID = 101L;
    private static final Long COUNTRY_ID  = 201L;

    @Test
    @DisplayName("Tam depo override → LocationFormatOverride döner")
    void resolveActiveFormat_fullOverride() {
        LocationFormatOverride override = LocationFormatOverride.builder()
                .locationId(LOCATION_ID)
                .dateFormat("dd.MM.yyyy")
                .timeFormat("HH:mm")
                .decimalSeparator(",")
                .thousandSeparator(".")
                .build();

        when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                .thenReturn(Optional.of(override));

        ActiveFormatResponse response = formatConfigService.resolveActiveFormat(LOCATION_ID);

        assertThat(response.dateFormat()).isEqualTo("dd.MM.yyyy");
        assertThat(response.decimalSeparator()).isEqualTo(",");
        verifyNoInteractions(locationCountryResolver);
    }

    @Test
    @DisplayName("Override yok → ülke varsayılanı kullanılır")
    void resolveActiveFormat_countryFallback() {
        when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                .thenReturn(Optional.empty());
        when(locationCountryResolver.resolveCountryId(LOCATION_ID))
                .thenReturn(Optional.of(COUNTRY_ID));
        when(countryFormatConfigRepository.findByCountryId(COUNTRY_ID))
                .thenReturn(Optional.of(CountryFormatConfig.builder()
                        .countryId(COUNTRY_ID)
                        .dateFormat("dd.MM.yyyy")
                        .timeFormat("HH:mm")
                        .decimalSeparator(",")
                        .thousandSeparator(".")
                        .build()));

        ActiveFormatResponse response = formatConfigService.resolveActiveFormat(LOCATION_ID);

        assertThat(response.dateFormat()).isEqualTo("dd.MM.yyyy");
        assertThat(response.thousandSeparator()).isEqualTo(".");
    }

    @Test
    @DisplayName("locationId null → uygulama varsayılanları, resolver çağrılmaz")
    void resolveActiveFormat_nullLocationId_returnsDefaults() {
        ActiveFormatResponse response = formatConfigService.resolveActiveFormat(null);

        assertThat(response.dateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(response.decimalSeparator()).isEqualTo(".");
        verifyNoInteractions(locationFormatOverrideRepository, locationCountryResolver);
    }

    @Test
    @DisplayName("Hiçbir kayıt yok → uygulama varsayılanları")
    void resolveActiveFormat_applicationDefaults() {
        when(locationFormatOverrideRepository.findByLocationId(LOCATION_ID))
                .thenReturn(Optional.empty());
        when(locationCountryResolver.resolveCountryId(LOCATION_ID))
                .thenReturn(Optional.empty());

        ActiveFormatResponse response = formatConfigService.resolveActiveFormat(LOCATION_ID);

        assertThat(response.dateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(response.decimalSeparator()).isEqualTo(".");
    }
}
