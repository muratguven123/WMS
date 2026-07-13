package com.wms.core.service;

import com.wms.core.dto.org.CreateLocationRequest;
import com.wms.core.dto.org.LocationDetailDto;
import com.wms.core.entity.Company;
import com.wms.core.entity.Country;
import com.wms.core.entity.Location;
import com.wms.core.entity.Region;
import com.wms.core.entity.User;
import com.wms.core.entity.enums.LocationType;
import com.wms.core.exception.BusinessException;
import com.wms.core.messaging.LocationProvisionedEventFactory;
import com.wms.core.repository.CompanyRepository;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.RegionRepository;
import com.wms.core.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationManagementServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationProvisioningService locationProvisioningService;
    @Mock private LocationProvisionedEventFactory locationProvisionedEventFactory;

    @InjectMocks
    private LocationManagementService locationManagementService;

    private static final Long COMPANY_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long REGION_ID = 100L;
    private static final Long COUNTRY_ID = 1L;

    @BeforeEach
    void setUpSecurity() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("kc-user-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createLocation_provisionsAndPublishesEvent() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("TR Corp");
        Country country = new Country();
        country.setId(COUNTRY_ID);
        Region region = Region.builder().country(country).name("Marmara").build();
        region.setId(REGION_ID);
        User user = new User();
        user.setId(USER_ID);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(locationRepository.existsByCompanyIdAndNameIgnoreCaseAndIsActiveTrue(COMPANY_ID, "Ankara Depo"))
                .thenReturn(false);
        when(regionRepository.findById(REGION_ID)).thenReturn(Optional.of(region));
        when(locationRepository.findByCompanyIdAndIsActiveTrue(COMPANY_ID)).thenReturn(List.of());
        when(userRepository.findByKeycloakUserId("kc-user-1")).thenReturn(Optional.of(user));
        when(locationRepository.save(any(Location.class))).thenAnswer(inv -> {
            Location loc = inv.getArgument(0);
            loc.setId(99L);
            return loc;
        });

        CreateLocationRequest request = new CreateLocationRequest(
                "Ankara Depo", LocationType.CENTRAL, "Europe/Istanbul", REGION_ID, null);

        LocationDetailDto result = locationManagementService.createLocation(COMPANY_ID, request);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.name()).isEqualTo("Ankara Depo");
        verify(locationProvisioningService).provisionCoreResources(any(Location.class), eq(USER_ID), eq(null));
        verify(locationProvisionedEventFactory).publish(
                eq(COMPANY_ID), eq(99L), eq(COUNTRY_ID), eq(REGION_ID), eq("Europe/Istanbul"));
    }

    @Test
    void createLocation_rejectsDuplicateName() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(locationRepository.existsByCompanyIdAndNameIgnoreCaseAndIsActiveTrue(COMPANY_ID, "Tuzla"))
                .thenReturn(true);

        CreateLocationRequest request = new CreateLocationRequest(
                "Tuzla", LocationType.CENTRAL, "Europe/Istanbul", REGION_ID, null);

        assertThatThrownBy(() -> locationManagementService.createLocation(COMPANY_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
