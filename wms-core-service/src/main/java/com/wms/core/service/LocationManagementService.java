package com.wms.core.service;

import com.wms.core.dto.org.CreateLocationRequest;
import com.wms.core.dto.org.LocationDetailDto;
import com.wms.core.dto.org.UpdateLocationRequest;
import com.wms.core.entity.Company;
import com.wms.core.entity.Location;
import com.wms.core.entity.Region;
import com.wms.core.entity.User;
import com.wms.core.exception.BusinessException;
import com.wms.core.messaging.LocationProvisionedEventFactory;
import com.wms.core.repository.CompanyRepository;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.RegionRepository;
import com.wms.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationManagementService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final LocationProvisioningService locationProvisioningService;
    private final LocationProvisionedEventFactory locationProvisionedEventFactory;

    @Transactional(readOnly = true)
    public LocationDetailDto getLocation(Long companyId, Long locationId) {
        Location location = requireCompanyLocation(companyId, locationId);
        return toDetail(location);
    }

    @Transactional
    public LocationDetailDto createLocation(Long companyId, CreateLocationRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(
                        "Şirket bulunamadı. companyId=" + companyId,
                        HttpStatus.NOT_FOUND,
                        "COMPANY_NOT_FOUND"));

        if (locationRepository.existsByCompanyIdAndNameIgnoreCaseAndIsActiveTrue(companyId, request.name())) {
            throw new BusinessException(
                    "Bu şirkette aynı isimde depo zaten var: " + request.name(),
                    HttpStatus.CONFLICT,
                    "LOCATION_NAME_EXISTS");
        }

        Region region = regionRepository.findById(request.regionId())
                .orElseThrow(() -> new BusinessException(
                        "Bölge bulunamadı. regionId=" + request.regionId(),
                        HttpStatus.BAD_REQUEST,
                        "REGION_NOT_FOUND"));

        validateRegionForCompany(companyId, region);

        Location location = Location.builder()
                .company(company)
                .region(region)
                .name(request.name())
                .type(request.type())
                .timezone(request.timezone())
                .build();
        location = locationRepository.save(location);

        Long creatorUserId = resolveCurrentUserId();
        locationProvisioningService.provisionCoreResources(location, creatorUserId, request.templateLocationId());

        locationProvisionedEventFactory.publish(
                companyId,
                location.getId(),
                region.getCountry().getId(),
                region.getId(),
                location.getTimezone());

        log.info("Location created: id={}, name={}, companyId={}", location.getId(), location.getName(), companyId);
        return toDetail(location);
    }

    @Transactional
    public LocationDetailDto updateLocation(Long companyId, Long locationId, UpdateLocationRequest request) {
        Location location = requireCompanyLocation(companyId, locationId);

        if (request.name() != null && !request.name().isBlank()) {
            if (locationRepository.existsByCompanyIdAndNameIgnoreCaseAndIsActiveTrueAndIdNot(
                    companyId, request.name(), locationId)) {
                throw new BusinessException(
                        "Bu şirkette aynı isimde depo zaten var: " + request.name(),
                        HttpStatus.CONFLICT,
                        "LOCATION_NAME_EXISTS");
            }
            location.setName(request.name());
        }
        if (request.type() != null) {
            location.setType(request.type());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            location.setTimezone(request.timezone());
        }
        if (request.regionId() != null) {
            Region region = regionRepository.findById(request.regionId())
                    .orElseThrow(() -> new BusinessException(
                            "Bölge bulunamadı. regionId=" + request.regionId(),
                            HttpStatus.BAD_REQUEST,
                            "REGION_NOT_FOUND"));
            validateRegionForCompany(companyId, region);
            location.setRegion(region);
        }

        return toDetail(locationRepository.save(location));
    }

    @Transactional
    public LocationDetailDto deactivateLocation(Long companyId, Long locationId) {
        Location location = requireCompanyLocation(companyId, locationId);
        location.setActive(false);
        locationRepository.delete(location);
        log.info("Location deactivated: id={}, companyId={}", locationId, companyId);
        return toDetail(location);
    }

    private void validateRegionForCompany(Long companyId, Region region) {
        boolean companyHasLocations = !locationRepository.findByCompanyIdAndIsActiveTrue(companyId).isEmpty();
        if (!companyHasLocations) {
            return;
        }
        Long regionCountryId = region.getCountry().getId();
        boolean countryMatches = locationRepository.findByCompanyIdAndIsActiveTrue(companyId).stream()
                .anyMatch(loc -> loc.getRegion().getCountry().getId().equals(regionCountryId));
        if (!countryMatches) {
            throw new BusinessException(
                    "Bölge ülkesi şirketin mevcut depolarıyla uyumlu değil.",
                    HttpStatus.BAD_REQUEST,
                    "REGION_COUNTRY_MISMATCH");
        }
    }

    private Location requireCompanyLocation(Long companyId, Long locationId) {
        return locationRepository.findByCompanyIdAndId(companyId, locationId)
                .orElseThrow(() -> new BusinessException(
                        "Depo bulunamadı. companyId=" + companyId + ", locationId=" + locationId,
                        HttpStatus.NOT_FOUND,
                        "LOCATION_NOT_FOUND"));
    }

    private LocationDetailDto toDetail(Location location) {
        return new LocationDetailDto(
                location.getId(),
                location.getCompany().getId(),
                location.getRegion().getId(),
                location.getName(),
                location.getType(),
                location.getTimezone(),
                location.isActive());
    }

    private Long resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new BusinessException("Kimlik doğrulama gerekli.", HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        String keycloakUserId = jwtAuth.getToken().getSubject();
        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException(
                        "WMS kullanıcı kaydı bulunamadı.",
                        HttpStatus.FORBIDDEN,
                        "USER_NOT_PROVISIONED"));
        return user.getId();
    }
}
