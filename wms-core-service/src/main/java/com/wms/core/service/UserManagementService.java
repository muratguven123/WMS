package com.wms.core.service;

import com.wms.core.dto.user.CreateUserRequest;
import com.wms.core.dto.user.UserAccessDto;
import com.wms.core.dto.user.UserSummaryDto;
import com.wms.core.dto.user.UserPreferencesRequest;
import com.wms.core.dto.user.GrantAccessRequest;
import com.wms.core.entity.Company;
import com.wms.core.entity.Location;
import com.wms.core.entity.Role;
import com.wms.core.entity.User;
import com.wms.core.entity.UserAccess;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.CompanyRepository;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.RoleRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.repository.UserRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.auth.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private final ApplicationEventPublisher eventPublisher;

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

        // Audit user creation
        eventPublisher.publishEvent(new ConfigChangeEvent(
                this,
                User.class.getSimpleName(),
                user.getId(),
                "CREATE",
                List.of(new ConfigChangeEvent.FieldChange("username", null, user.getUsername())),
                TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
        ));

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
            access = userAccessRepository.save(access);
            accesses.add(access);

            // Audit access grant
            eventPublisher.publishEvent(new ConfigChangeEvent(
                    this,
                    UserAccess.class.getSimpleName(),
                    access.getId(),
                    "GRANT",
                    List.of(
                            new ConfigChangeEvent.FieldChange("userId", null, String.valueOf(user.getId())),
                            new ConfigChangeEvent.FieldChange("companyId", null, String.valueOf(input.companyId())),
                            new ConfigChangeEvent.FieldChange("locationId", null, input.locationId() != null ? String.valueOf(input.locationId()) : null),
                            new ConfigChangeEvent.FieldChange("roleId", null, String.valueOf(input.roleId()))
                    ),
                    TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
            ));
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

        // Audit deactivation
        eventPublisher.publishEvent(new ConfigChangeEvent(
                this,
                User.class.getSimpleName(),
                userId,
                "DEACTIVATE",
                List.of(new ConfigChangeEvent.FieldChange("active", "true", "false")),
                TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
        ));
    }

    @Transactional
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        if (user.getKeycloakUserId() != null) {
            keycloakAdminService.enableUser(user.getKeycloakUserId());
        }
        user.setActive(true);
        userRepository.save(user);

        // Audit activation
        eventPublisher.publishEvent(new ConfigChangeEvent(
                this,
                User.class.getSimpleName(),
                userId,
                "ACTIVATE",
                List.of(new ConfigChangeEvent.FieldChange("active", "false", "true")),
                TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
        ));
    }

    @Transactional
    public void updateUserRoles(Long userId, List<String> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        if (user.getKeycloakUserId() == null) {
            throw new BusinessException("Kullanıcının Keycloak ID'si bulunamadı", HttpStatus.BAD_REQUEST);
        }

        keycloakAdminService.updateUserRoles(user.getKeycloakUserId(), roles);

        // Audit role assignment
        eventPublisher.publishEvent(new ConfigChangeEvent(
                this,
                User.class.getSimpleName(),
                userId,
                "ROLE_ASSIGN",
                List.of(new ConfigChangeEvent.FieldChange("roles", null, String.join(",", roles))),
                TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
        ));
    }

    @Transactional
    public UserSummaryDto updatePreferences(Long userId, UserPreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        List<ConfigChangeEvent.FieldChange> changes = new ArrayList<>();
        if (!Objects.equals(user.getPreferredLanguage(), request.preferredLanguage())) {
            changes.add(new ConfigChangeEvent.FieldChange("preferredLanguage", user.getPreferredLanguage(), request.preferredLanguage()));
            user.setPreferredLanguage(request.preferredLanguage());
        }
        if (!Objects.equals(user.getPreferredTimezone(), request.preferredTimezone())) {
            changes.add(new ConfigChangeEvent.FieldChange("preferredTimezone", user.getPreferredTimezone(), request.preferredTimezone()));
            user.setPreferredTimezone(request.preferredTimezone());
        }

        if (!changes.isEmpty()) {
            user = userRepository.save(user);
            eventPublisher.publishEvent(new ConfigChangeEvent(
                    this,
                    User.class.getSimpleName(),
                    user.getId(),
                    "UPDATE",
                    changes,
                    TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
            ));
        }

        return toSummary(user);
    }

    @Transactional
    public UserSummaryDto grantAccess(Long userId, GrantAccessRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new BusinessException("Şirket bulunamadı", HttpStatus.BAD_REQUEST, "COMPANY_NOT_FOUND"));
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new BusinessException("Rol bulunamadı", HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND"));
        Location location = null;
        if (request.locationId() != null) {
            location = locationRepository.findById(request.locationId())
                    .orElseThrow(() -> new BusinessException("Lokasyon bulunamadı", HttpStatus.BAD_REQUEST, "LOCATION_NOT_FOUND"));
        }

        UserAccess access = UserAccess.builder()
                .user(user)
                .company(company)
                .location(location)
                .role(role)
                .build();
        access = userAccessRepository.save(access);

        // Audit grant access
        List<ConfigChangeEvent.FieldChange> changes = List.of(
                new ConfigChangeEvent.FieldChange("userId", null, String.valueOf(userId)),
                new ConfigChangeEvent.FieldChange("companyId", null, String.valueOf(request.companyId())),
                new ConfigChangeEvent.FieldChange("locationId", null, request.locationId() != null ? String.valueOf(request.locationId()) : null),
                new ConfigChangeEvent.FieldChange("roleId", null, String.valueOf(request.roleId()))
        );
        eventPublisher.publishEvent(new ConfigChangeEvent(
                this,
                UserAccess.class.getSimpleName(),
                access.getId(),
                "GRANT",
                changes,
                TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
        ));

        return toSummary(user);
    }

    @Transactional
    public UserSummaryDto revokeAccess(Long userId, Long accessId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Kullanıcı bulunamadı", HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        UserAccess access = userAccessRepository.findById(accessId)
                .orElseThrow(() -> new BusinessException("Erişim yetkisi bulunamadı", HttpStatus.NOT_FOUND, "ACCESS_NOT_FOUND"));

        if (!access.getUser().getId().equals(userId)) {
            throw new BusinessException("Bu erişim yetkisi belirtilen kullanıcıya ait değil", HttpStatus.BAD_REQUEST);
        }

        userAccessRepository.delete(access);

        // Audit revoke access
        List<ConfigChangeEvent.FieldChange> changes = List.of(
                new ConfigChangeEvent.FieldChange("userId", String.valueOf(userId), null),
                new ConfigChangeEvent.FieldChange("companyId", String.valueOf(access.getCompany().getId()), null),
                new ConfigChangeEvent.FieldChange("locationId", access.getLocation() != null ? String.valueOf(access.getLocation().getId()) : null, null),
                new ConfigChangeEvent.FieldChange("roleId", String.valueOf(access.getRole().getId()), null)
        );
        eventPublisher.publishEvent(new ConfigChangeEvent(
                this,
                UserAccess.class.getSimpleName(),
                accessId,
                "REVOKE",
                changes,
                TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
        ));

        return toSummary(user);
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
                accessDtos,
                user.getPreferredLanguage(),
                user.getPreferredTimezone());
    }
}
