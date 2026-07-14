package com.wms.core.service;

import com.wms.core.dto.user.GrantAccessRequest;
import com.wms.core.dto.user.UserPreferencesRequest;
import com.wms.core.dto.user.UserSummaryDto;
import com.wms.core.entity.Company;
import com.wms.core.entity.Location;
import com.wms.core.entity.Role;
import com.wms.core.entity.User;
import com.wms.core.entity.UserAccess;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.repository.CompanyRepository;
import com.wms.core.repository.LocationRepository;
import com.wms.core.repository.RoleRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.repository.UserRepository;
import com.wms.core.service.auth.KeycloakAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementService Tests")
class UserManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAccessRepository userAccessRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserManagementService userManagementService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .username("testuser")
                .email("test@wms.com")
                .keycloakUserId("kc-user-id")
                .build();
        user.setId(100L);
        user.setActive(true);
    }

    @Test
    @DisplayName("updatePreferences — updates language and timezone, publishes ConfigChangeEvent")
    void updatePreferences_updatesFieldsAndPublishesEvent() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userAccessRepository.findByUserIdWithCompany(100L)).thenReturn(Collections.emptyList());

        UserPreferencesRequest request = new UserPreferencesRequest("tr", "Europe/Istanbul");
        UserSummaryDto response = userManagementService.updatePreferences(100L, request);

        assertThat(response.preferredLanguage()).isEqualTo("tr");
        assertThat(response.preferredTimezone()).isEqualTo("Europe/Istanbul");

        ArgumentCaptor<ConfigChangeEvent> eventCaptor = ArgumentCaptor.forClass(ConfigChangeEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ConfigChangeEvent event = eventCaptor.getValue();
        assertThat(event.getEntityName()).isEqualTo("User");
        assertThat(event.getEntityId()).isEqualTo(100L);
        assertThat(event.getActionType()).isEqualTo("UPDATE");
        assertThat(event.getChanges()).hasSize(2);
    }

    @Test
    @DisplayName("activateUser — activates user, calls keycloak, publishes ConfigChangeEvent")
    void activateUser_activatesAndPublishesEvent() {
        user.setActive(false);
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        userManagementService.activateUser(100L);

        assertThat(user.isActive()).isTrue();
        verify(keycloakAdminService).enableUser("kc-user-id");

        ArgumentCaptor<ConfigChangeEvent> eventCaptor = ArgumentCaptor.forClass(ConfigChangeEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ConfigChangeEvent event = eventCaptor.getValue();
        assertThat(event.getEntityName()).isEqualTo("User");
        assertThat(event.getActionType()).isEqualTo("ACTIVATE");
    }

    @Test
    @DisplayName("deactivateUser — deactivates user, calls keycloak, publishes ConfigChangeEvent")
    void deactivateUser_deactivatesAndPublishesEvent() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        userManagementService.deactivateUser(100L);

        assertThat(user.isActive()).isFalse();
        verify(keycloakAdminService).disableUser("kc-user-id");

        ArgumentCaptor<ConfigChangeEvent> eventCaptor = ArgumentCaptor.forClass(ConfigChangeEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ConfigChangeEvent event = eventCaptor.getValue();
        assertThat(event.getEntityName()).isEqualTo("User");
        assertThat(event.getActionType()).isEqualTo("DEACTIVATE");
    }

    @Test
    @DisplayName("grantAccess — grants access and publishes ConfigChangeEvent")
    void grantAccess_grantsPrivilegeAndPublishesEvent() {
        Company company = new Company();
        company.setId(1L);
        Role role = new Role();
        role.setId(2L);
        Location location = new Location();
        location.setId(3L);

        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(location));

        UserAccess access = UserAccess.builder().id(50L).user(user).company(company).location(location).role(role).build();
        when(userAccessRepository.save(any(UserAccess.class))).thenReturn(access);

        GrantAccessRequest request = new GrantAccessRequest(1L, 3L, 2L);
        userManagementService.grantAccess(100L, request);

        ArgumentCaptor<ConfigChangeEvent> eventCaptor = ArgumentCaptor.forClass(ConfigChangeEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ConfigChangeEvent event = eventCaptor.getValue();
        assertThat(event.getEntityName()).isEqualTo("UserAccess");
        assertThat(event.getActionType()).isEqualTo("GRANT");
        assertThat(event.getEntityId()).isEqualTo(50L);
    }
}
