package com.wms.core.service;

import com.wms.core.dto.org.CompanySummaryDto;
import com.wms.core.dto.org.LocationSummaryDto;
import com.wms.core.dto.org.RegionSummaryDto;
import com.wms.core.entity.Company;
import com.wms.core.entity.Location;
import com.wms.core.entity.Region;
import com.wms.core.entity.User;
import com.wms.core.entity.UserAccess;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.RegionRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final UserRepository userRepository;
    private final UserAccessRepository userAccessRepository;
    private final LocationRepository locationRepository;
    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public List<CompanySummaryDto> listAccessibleCompanies() {
        Long userId = resolveCurrentUserId();
        List<UserAccess> accesses = userAccessRepository.findByUserIdWithCompany(userId);

        Map<Long, CompanySummaryDto> companies = new LinkedHashMap<>();
        for (UserAccess access : accesses) {
            Company company = access.getCompany();
            companies.putIfAbsent(company.getId(), new CompanySummaryDto(
                    company.getId(),
                    company.getName(),
                    company.getTaxNumber()));
        }
        return new ArrayList<>(companies.values());
    }

    @Transactional(readOnly = true)
    public List<LocationSummaryDto> listAccessibleLocations(Long companyId) {
        Long userId = resolveCurrentUserId();
        List<UserAccess> accesses = userAccessRepository.findByUserIdAndCompanyIdWithLocation(userId, companyId);

        if (accesses.isEmpty()) {
            throw new BusinessException(
                    "Bu şirkete erişim yetkiniz yok. companyId=" + companyId,
                    HttpStatus.FORBIDDEN,
                    "ORG_ACCESS_DENIED");
        }

        boolean allLocations = accesses.stream().anyMatch(a -> a.getLocation() == null);

        List<Location> locations = allLocations
                ? locationRepository.findByCompanyIdAndIsActiveTrue(companyId)
                : accesses.stream()
                        .map(UserAccess::getLocation)
                        .filter(loc -> loc != null && loc.isActive())
                        .distinct()
                        .toList();

        return locations.stream()
                .map(loc -> new LocationSummaryDto(
                        loc.getId(),
                        companyId,
                        loc.getName(),
                        loc.getTimezone(),
                        loc.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegionSummaryDto> listRegionsByCountry(Long countryId) {
        return regionRepository.findByCountryId(countryId).stream()
                .map(region -> new RegionSummaryDto(
                        region.getId(),
                        countryId,
                        region.getName()))
                .toList();
    }

    private Long resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new BusinessException(
                    "Kimlik doğrulama gerekli.",
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_REQUIRED");
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
