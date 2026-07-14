package com.wms.core.service;

import com.wms.core.dto.workflow.WorkflowEnforceRequest;
import com.wms.core.dto.workflow.WorkflowEnforceResponse;
import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.LocationProcessConfig;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.ProcessDefinition;
import com.wms.core.entity.ProcessStepDefinition;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEnforcementService — uzak enforce API")
class WorkflowEnforcementServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 1L;
    private static final Long LOCATION_ID = 1L;
    private static final Long PROCESS_CONFIG_ID = 10L;
    private static final Long STEP_CONFIG_ID = 20L;

    @Mock private LocationProcessConfigRepository locationProcessConfigRepository;
    @Mock private LocationProcessStepConfigRepository locationProcessStepConfigRepository;
    @Mock private UserAccessRepository userAccessRepository;
    @Mock private ApprovalRequestService approvalRequestService;

    @InjectMocks
    private WorkflowEnforcementService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setContext(new TenantContext(USER_ID, COMPANY_ID, LOCATION_ID));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Aktif adım yoksa BYPASSED döner")
    void enforce_passiveStep_returnsBypassed() {
        when(locationProcessConfigRepository.findActiveByLocationId(LOCATION_ID)).thenReturn(List.of());

        WorkflowEnforceResponse response = service.enforce(
                new WorkflowEnforceRequest("OUTBOUND", "PICKING", 99L, "PICKING_LIST"));

        assertThat(response.decision()).isEqualTo(WorkflowEnforceResponse.Decision.BYPASSED);
        assertThat(response.processCode()).isEqualTo("OUTBOUND");
        assertThat(response.stepCode()).isEqualTo("PICKING");
    }

    @Test
    @DisplayName("Aktif onaysız adım ALLOWED döner")
    void enforce_activeStep_returnsAllowed() {
        stubOutboundPicking(false);

        WorkflowEnforceResponse response = service.enforce(
                new WorkflowEnforceRequest("OUTBOUND", "PICKING", 99L, "PICKING_LIST"));

        assertThat(response.decision()).isEqualTo(WorkflowEnforceResponse.Decision.ALLOWED);
        assertThat(response.stepConfigId()).isEqualTo(STEP_CONFIG_ID);
    }

    @Test
    @DisplayName("Onay gereken adım APPROVAL_REQUIRED döner")
    void enforce_requiresApproval_returnsApprovalRequired() {
        stubOutboundPicking(true);
        ApprovalRequest approval = mock(ApprovalRequest.class);
        when(approval.getId()).thenReturn(55L);
        when(approvalRequestService.createPendingRequest(STEP_CONFIG_ID, "PICKING_LIST", 99L, USER_ID))
                .thenReturn(approval);

        WorkflowEnforceResponse response = service.enforce(
                new WorkflowEnforceRequest("OUTBOUND", "PICKING", 99L, "PICKING_LIST"));

        assertThat(response.decision()).isEqualTo(WorkflowEnforceResponse.Decision.APPROVAL_REQUIRED);
        assertThat(response.approvalRequestId()).isEqualTo(55L);
        assertThat(response.stepConfigId()).isEqualTo(STEP_CONFIG_ID);
    }

    private void stubOutboundPicking(boolean requiresApproval) {
        ProcessDefinition processDefinition = mock(ProcessDefinition.class);
        when(processDefinition.getCode()).thenReturn("OUTBOUND");

        LocationProcessConfig processConfig = mock(LocationProcessConfig.class);
        when(processConfig.getProcessDefinition()).thenReturn(processDefinition);
        when(processConfig.getId()).thenReturn(PROCESS_CONFIG_ID);

        ProcessStepDefinition stepDefinition = mock(ProcessStepDefinition.class);
        when(stepDefinition.getCode()).thenReturn("PICKING");

        LocationProcessStepConfig stepConfig = mock(LocationProcessStepConfig.class);
        when(stepConfig.getProcessStepDefinition()).thenReturn(stepDefinition);
        when(stepConfig.getId()).thenReturn(STEP_CONFIG_ID);
        when(stepConfig.getResponsibleRoleId()).thenReturn(null);
        when(stepConfig.isRequiresApproval()).thenReturn(requiresApproval);

        when(locationProcessConfigRepository.findActiveByLocationId(LOCATION_ID))
                .thenReturn(List.of(processConfig));
        when(locationProcessStepConfigRepository.findActiveStepsByConfigId(PROCESS_CONFIG_ID))
                .thenReturn(List.of(stepConfig));
    }
}
