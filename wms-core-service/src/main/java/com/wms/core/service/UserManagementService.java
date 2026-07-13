package com.wms.core.service;

import com.wms.core.dto.user.CreateUserRequest;
import com.wms.core.dto.user.UserAccessDto;
import com.wms.core.dto.user.UserSummaryDto;
import com.wms.core.entity.Company;
import com.wms.core.entity.Location;
import com.wms.core.entity.Role;
import com.wms.core.entity.User;
import com.wms.core.entity.UserAccess;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.CompanyRepository;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.RoleRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.repository.UserRepository;
import com.wms.core.service.auth.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserAccessRepository userAccessRepository;
    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final RoleRepository roleRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public UserSummaryDto createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("Bu kullanıcı adı zaten kullanılıyor", HttpStatus.CONFLICT, "USER_EXISTS");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Bu e-posta zaten kayıtlı", HttpStatus.CONFLICT, "EMAIL_EXISTS");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash("KEYCLOAK_MANAGED")
                .build();
        user = userRepository.save(user);

        List<UserAccess> accesses = new ArrayList<>();
        for (CreateUserRequest.UserAccessInput input : request.accesses()) {
            Company company = companyRepository.findById(input.companyId())
                    .orElseThrow(() -> new BusinessException("Şirket bulunamadı", HttpStatus.BAD_REQUEST, "COMPANY_NOT_FOUND"));
            Role role = roleRepository.findById(input.roleId())
                    .orElseThrow(() -> new BusinessException("Rol bulunamadı", HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND"));
            Location location = null;
            if (input.locationId() != null) {
                location = locationRepository.findById(input.locationId())
                        .orElseThrow(() -> new BusinessException("Lokasyon bulunamadı", HttpStatus.BAD_REQUEST, "LOCATION_NOT_FOUND"));
            }
            UserAccess access = UserAccess.builder()
                    .user(user)
                    .company(company)
                    .location(location)
                    .role(role)
                    .build();
            accesses.add(userAccessRepository.save(access));
        }

        try {
            String keycloakUserId = keycloakAdminService.createUser(
                    request.username(),
                    request.email(),
                    request.password(),
                    request.firstName(),
                    request.lastName(),
                    user.getId(),
                    request.keycloakRoles());
            user.setKeycloakUserId(keycloakUserId);
            user = userRepository.save(user);
        } catch (Exception ex) {
            log.error("Keycloak provisioning başarısız, WMS kullanıcısı geri alınıyor: {}", ex.getMessage());
            throw ex instanceof BusinessException be ? be :
                    new BusinessException("Kullanıcı oluşturulamadı: " + ex.getMessage(),
                            HttpStatus.BAD_GATEWAY, "KC_PROVISION_FAILED");
        }

        log.info("Yeni kullanıcı oluşturuldu → username={}, id={}", user.getUsername(), user.getId());
        return toSummary(user);
    }

    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        if (user.getKeycloakUserId() != null) {
            keycloakAdminService.disableUser(user.getKeycloakUserId());
        }
        user.setActive(false);
        userRepository.save(user);
    }

    private UserSummaryDto toSummary(User user) {
        List<UserAccess> accesses = userAccessRepository.findByUserIdWithCompany(user.getId());
        List<UserAccessDto> accessDtos = accesses.stream()
                .map(ua -> new UserAccessDto(
                        ua.getId(),
                        ua.getCompany().getId(),
                        ua.getCompany().getName(),
                        ua.getLocation() != null ? ua.getLocation().getId() : null,
                        ua.getLocation() != null ? ua.getLocation().getName() : null,
                        ua.getRole().getId(),
                        ua.getRole().getName()))
                .toList();

        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getKeycloakUserId(),
                user.isActive(),
                accessDtos);
    }
}
