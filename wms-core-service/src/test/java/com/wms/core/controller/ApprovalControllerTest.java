package com.wms.core.controller;

import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.enums.ApprovalStatus;
import com.wms.core.exception.GlobalExceptionHandler;
import com.wms.core.repository.ApprovalRequestRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextFilter;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.ApprovalRequestService;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ApprovalController")
class ApprovalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ApprovalRequestService approvalRequestService;
    @MockBean private ApprovalRequestRepository approvalRequestRepository;
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
    @DisplayName("GET /api/approvals/pending — bekleyen onayları listeler")
    void listPending_returnsPendingApprovals() throws Exception {
        Long approvalId = 1L;
        Long stepConfigId = 1L;
        Long referenceId = 1L;
        Long requestedBy = 1L;

        ApprovalRequest pending = ApprovalRequest.builder()
                .id(approvalId)
                .stepConfigId(stepConfigId)
                .referenceType("RECEIPT")
                .referenceId(referenceId)
                .requestedByUserId(requestedBy)
                .status(ApprovalStatus.PENDING_APPROVAL)
                .build();

        when(approvalRequestRepository.findByStatusAndCompanyIdAndLocationIdOrderByCreatedAtAsc(
                ApprovalStatus.PENDING_APPROVAL, 1L, 1L)).thenReturn(List.of(pending));

        mockMvc.perform(get("/api/approvals/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(approvalId.toString()))
                .andExpect(jsonPath("$[0].referenceType").value("RECEIPT"))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }
}
