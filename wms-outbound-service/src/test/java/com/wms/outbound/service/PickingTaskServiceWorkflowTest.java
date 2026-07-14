package com.wms.outbound.service;

import com.wms.outbound.dto.WorkflowEnforceResponse;
import com.wms.outbound.entity.PickingList;
import com.wms.outbound.entity.enums.PickingListStatus;
import com.wms.outbound.exception.ApprovalRequiredException;
import com.wms.outbound.integration.CoreServiceClient;
import com.wms.outbound.messaging.TaskEventPublisher;
import com.wms.outbound.repository.PickingListRepository;
import com.wms.outbound.security.TenantContext;
import com.wms.outbound.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PickingTaskService — workflow enforce")
class PickingTaskServiceWorkflowTest {

    @Mock private PickingListRepository pickingListRepository;
    @Mock private PickingRoutingService pickingRoutingService;
    @Mock private TaskEventPublisher taskEventPublisher;
    @Mock private CoreServiceClient coreServiceClient;

    @InjectMocks
    private PickingTaskService pickingTaskService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setContext(new TenantContext(1L, 1L, 1L));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Adım BYPASSED iken start devam eder")
    void start_whenWorkflowBypassed_proceeds() {
        PickingList list = PickingList.builder()
                .id(5L)
                .companyId(1L)
                .warehouseLocationId(1L)
                .status(PickingListStatus.ASSIGNED)
                .assignedUserId(1L)
                .items(new ArrayList<>())
                .build();
        when(pickingListRepository.findWithDetailsById(5L)).thenReturn(Optional.of(list));
        when(pickingListRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(coreServiceClient.enforceWorkflowStep("OUTBOUND", "PICKING", 5L, "PICKING_LIST"))
                .thenReturn(new WorkflowEnforceResponse(
                        WorkflowEnforceResponse.Decision.BYPASSED, null, null, "OUTBOUND", "PICKING"));
        when(pickingRoutingService.mapToResponse(any())).thenReturn(null);

        pickingTaskService.start(5L);

        verify(coreServiceClient).enforceWorkflowStep("OUTBOUND", "PICKING", 5L, "PICKING_LIST");
        verify(pickingListRepository).save(any());
    }

    @Test
    @DisplayName("Onay gerekliyse start ApprovalRequiredException fırlatır")
    void start_whenApprovalRequired_throws() {
        PickingList list = PickingList.builder()
                .id(5L)
                .companyId(1L)
                .warehouseLocationId(1L)
                .status(PickingListStatus.ASSIGNED)
                .assignedUserId(1L)
                .items(new ArrayList<>())
                .build();
        when(pickingListRepository.findWithDetailsById(5L)).thenReturn(Optional.of(list));
        when(coreServiceClient.enforceWorkflowStep("OUTBOUND", "PICKING", 5L, "PICKING_LIST"))
                .thenThrow(new ApprovalRequiredException(42L));

        assertThatThrownBy(() -> pickingTaskService.start(5L))
                .isInstanceOf(ApprovalRequiredException.class);

        verify(pickingListRepository, never()).save(any());
    }
}
