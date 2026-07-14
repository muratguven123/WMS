package com.wms.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.core.dto.user.UserSummaryDto;
import com.wms.core.dto.user.UserPreferencesRequest;
import com.wms.core.dto.user.GrantAccessRequest;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.repository.RoleRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.UserManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("UserManagementController")
class UserManagementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private UserManagementService userManagementService;
    @MockBean private RoleRepository roleRepository;
    @MockBean private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setUpSecurity() {
        ControllerTestSecuritySupport.authenticateAsWmsAdmin();
        TenantContextHolder.setContext(new TenantContext(1L, 1L, 1L));
    }

    @AfterEach
    void clearSecurity() {
        ControllerTestSecuritySupport.clearAuthentication();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("PATCH /api/users/{id}/preferences — updates user preferences successfully")
    void updatePreferences_returnsUpdatedPreferences() throws Exception {
        UserPreferencesRequest request = new UserPreferencesRequest("tr", "Europe/Istanbul");
        UserSummaryDto response = new UserSummaryDto(100L, "testuser", "test@wms.com", "kc-id", true, Collections.emptyList(), "tr", "Europe/Istanbul");

        when(userManagementService.updatePreferences(eq(100L), any(UserPreferencesRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/100/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("tr"))
                .andExpect(jsonPath("$.preferredTimezone").value("Europe/Istanbul"));

        verify(userManagementService).updatePreferences(eq(100L), any(UserPreferencesRequest.class));
    }

    @Test
    @DisplayName("POST /api/users/{id}/access — grants location access successfully")
    void grantAccess_returnsUserSummary() throws Exception {
        GrantAccessRequest request = new GrantAccessRequest(1L, 3L, 2L);
        UserSummaryDto response = new UserSummaryDto(100L, "testuser", "test@wms.com", "kc-id", true, Collections.emptyList(), null, null);

        when(userManagementService.grantAccess(eq(100L), any(GrantAccessRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/users/100/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userManagementService).grantAccess(eq(100L), any(GrantAccessRequest.class));
    }
}
