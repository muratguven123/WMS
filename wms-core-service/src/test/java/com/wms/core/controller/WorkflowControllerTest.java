package com.wms.core.controller;

import com.wms.core.dto.workflow.ProcessStepDto;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.WorkflowValidatorService;
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

@WebMvcTest(WorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("WorkflowController")
class WorkflowControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 1L;
    private static final Long LOCATION_ID = 1L;

    @Autowired private MockMvc mockMvc;

    @MockBean private WorkflowValidatorService workflowValidatorService;
    @MockBean private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setContext() {
        ControllerTestSecuritySupport.authenticateAsWmsAdmin();
        TenantContextHolder.setContext(new TenantContext(USER_ID, COMPANY_ID, LOCATION_ID));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        ControllerTestSecuritySupport.clearAuthentication();
    }

    @Test
    @DisplayName("GET /api/workflow/next-step — bir sonraki adımı döner")
    void determineNextStep_returnsNextStep() throws Exception {
        ProcessStepDto nextStep = ProcessStepDto.builder()
                .stepCode("QC")
                .stepName("Kalite Kontrol")
                .sequence(2)
                .mandatory(true)
                .requiresApproval(false)
                .errorStrategy(ErrorStrategy.BLOCK)
                .processCompleted(false)
                .build();

        when(workflowValidatorService.determineNextStep(
                eq(LOCATION_ID), eq("INBOUND"), eq("RECEIPT"), eq(false)))
                .thenReturn(nextStep);

        mockMvc.perform(get("/api/workflow/next-step")
                        .param("processCode", "INBOUND")
                        .param("currentStepCode", "RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepCode").value("QC"))
                .andExpect(jsonPath("$.stepName").value("Kalite Kontrol"))
                .andExpect(jsonPath("$.sequence").value(2))
                .andExpect(jsonPath("$.mandatory").value(true))
                .andExpect(jsonPath("$.processCompleted").value(false));
    }
}
