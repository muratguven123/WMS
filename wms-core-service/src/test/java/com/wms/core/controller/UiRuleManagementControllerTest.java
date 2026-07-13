package com.wms.core.controller;

import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.service.UiRuleManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiRuleManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("UiRuleManagementController")
class UiRuleManagementControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UiRuleManagementService ruleManagementService;
    @MockBean private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setUpSecurity() {
        ControllerTestSecuritySupport.authenticateAsWmsAdmin();
    }

    @AfterEach
    void clearSecurity() {
        ControllerTestSecuritySupport.clearAuthentication();
    }

    @Test
    @DisplayName("DELETE /api/ui/rules/{id} — kuralı siler")
    void deleteRule_returnsNoContent() throws Exception {
        Long ruleId = 1L;

        mockMvc.perform(delete("/api/ui/rules/{ruleId}", ruleId))
                .andExpect(status().isNoContent());

        verify(ruleManagementService).deleteRule(ruleId);
    }
}
