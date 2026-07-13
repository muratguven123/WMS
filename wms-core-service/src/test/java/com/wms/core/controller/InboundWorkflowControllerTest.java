package com.wms.core.controller;

import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.service.InboundWorkflowService;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InboundWorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("InboundWorkflowController")
class InboundWorkflowControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private InboundWorkflowService inboundWorkflowService;
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
    @DisplayName("POST /api/inbound/receipts/{id}/qc — QC adımını tamamlar")
    void completeQc_returnsCompleted() throws Exception {
        Long receiptId = 1L;

        mockMvc.perform(post("/api/inbound/receipts/{receiptId}/qc", receiptId)
                        .param("result", "PASS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.step").value("QC"));

        verify(inboundWorkflowService).completeQcStep(eq(receiptId), eq("PASS"));
    }
}
