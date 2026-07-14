package com.wms.core.service;

import com.wms.core.dto.workflow.ProcessStepDefinitionRequest;
import com.wms.core.dto.workflow.UpdateProcessStepDefinitionRequest;
import com.wms.core.dto.workflow.ProcessStepDefinitionResponse;
import com.wms.core.entity.LocationProcessConfig;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.ProcessDefinition;
import com.wms.core.entity.ProcessStepDefinition;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import com.wms.core.repository.ProcessDefinitionRepository;
import com.wms.core.repository.ProcessStepDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessStepDefinitionService Tests")
class ProcessStepDefinitionServiceTest {

    @Mock private ProcessStepDefinitionRepository stepDefinitionRepository;
    @Mock private LocationProcessConfigRepository locationProcessConfigRepository;
    @Mock private LocationProcessStepConfigRepository locationProcessStepConfigRepository;
    @Mock private ProcessDefinitionRepository processDefinitionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProcessStepDefinitionService processStepDefinitionService;

    private ProcessDefinition processDefinition;
    private ProcessStepDefinition stepDef;

    @BeforeEach
    void setUp() {
        processDefinition = new ProcessDefinition();
        processDefinition.setId(1L);
        processDefinition.setCode("INBOUND");

        stepDef = ProcessStepDefinition.builder()
                .processDefinition(processDefinition)
                .code("QC")
                .name("Quality Control")
                .defaultSequence(20)
                .build();
        stepDef.setId(10L);
        stepDef.setActive(true);
    }

    @Test
    @DisplayName("create — creates step definition, syncs passive step configs to locations, publishes events")
    void create_createsDefinitionAndSyncsConfigs() {
        when(processDefinitionRepository.findById(1L)).thenReturn(Optional.of(processDefinition));
        when(stepDefinitionRepository.save(any(ProcessStepDefinition.class))).thenReturn(stepDef);

        LocationProcessConfig lpc = new LocationProcessConfig();
        lpc.setId(5L);
        when(locationProcessConfigRepository.findByProcessDefinitionId(1L)).thenReturn(List.of(lpc));

        LocationProcessStepConfig lpsc = new LocationProcessStepConfig();
        lpsc.setId(8L);
        lpsc.setLocationProcessConfig(lpc);
        when(locationProcessStepConfigRepository.save(any(LocationProcessStepConfig.class))).thenReturn(lpsc);

        ProcessStepDefinitionRequest request = new ProcessStepDefinitionRequest(1L, "QC", "Quality Control", 20);
        ProcessStepDefinitionResponse response = processStepDefinitionService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        verify(locationProcessStepConfigRepository).save(any(LocationProcessStepConfig.class));
        verify(eventPublisher, times(2)).publishEvent(any());
    }

    @Test
    @DisplayName("update — throws warning when deactivating in-use step without force")
    void update_deactivateInUseStep_throwsConflictWithoutForce() {
        when(stepDefinitionRepository.findById(10L)).thenReturn(Optional.of(stepDef));
        when(locationProcessStepConfigRepository.countByProcessStepDefinitionIdAndIsActiveTrue(10L)).thenReturn(3L);

        UpdateProcessStepDefinitionRequest request = new UpdateProcessStepDefinitionRequest("Quality Control", 20, false);

        assertThatThrownBy(() -> processStepDefinitionService.update(10L, request, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("STEP_IN_USE_WARNING");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("update — allows deactivating in-use step with force")
    void update_deactivateInUseStep_allowsWithForce() {
        when(stepDefinitionRepository.findById(10L)).thenReturn(Optional.of(stepDef));
        when(locationProcessStepConfigRepository.countByProcessStepDefinitionIdAndIsActiveTrue(10L)).thenReturn(3L);
        when(stepDefinitionRepository.save(any(ProcessStepDefinition.class))).thenReturn(stepDef);

        UpdateProcessStepDefinitionRequest request = new UpdateProcessStepDefinitionRequest("Quality Control", 20, false);
        ProcessStepDefinitionResponse response = processStepDefinitionService.update(10L, request, true);

        assertThat(response.isActive()).isFalse();
        verify(stepDefinitionRepository).save(stepDef);
    }
}
