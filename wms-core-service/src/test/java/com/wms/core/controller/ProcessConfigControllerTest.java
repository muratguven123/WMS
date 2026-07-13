package com.wms.core.controller;

import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.service.ProcessConfigService;
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


import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ProcessConfigController")
class ProcessConfigControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProcessConfigService processConfigService;
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
    @DisplayName("GET /api/process-config/steps/{id} — adım konfigürasyonunu döner")
    void getStepConfig_returnsConfig() throws Exception {
        Long stepConfigId = 1L;
        Long responsibleRoleId = 1L;

        LocationProcessStepConfig config = LocationProcessStepConfig.builder()
                .sequence(3)
                .isMandatory(true)
                .responsibleRoleId(responsibleRoleId)
                .requiresApproval(true)
                .errorStrategy(ErrorStrategy.BLOCK)
                .build();
        config.setId(stepConfigId);

        when(processConfigService.getStepConfig(eq(stepConfigId))).thenReturn(config);

        mockMvc.perform(get("/api/process-config/steps/{stepConfigId}", stepConfigId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stepConfigId.toString()))
                .andExpect(jsonPath("$.sequence").value(3))
                .andExpect(jsonPath("$.mandatory").value(true))
                .andExpect(jsonPath("$.requiresApproval").value(true))
                .andExpect(jsonPath("$.errorStrategy").value("BLOCK"));
    }
}
