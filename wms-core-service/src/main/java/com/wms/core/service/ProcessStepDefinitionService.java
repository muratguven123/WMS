package com.wms.core.service;

import com.wms.core.dto.workflow.ProcessStepDefinitionRequest;
import com.wms.core.dto.workflow.UpdateProcessStepDefinitionRequest;
import com.wms.core.dto.workflow.ProcessStepDefinitionResponse;
import com.wms.core.entity.LocationProcessConfig;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.ProcessDefinition;
import com.wms.core.entity.ProcessStepDefinition;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.event.ConfigChangeEvent;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import com.wms.core.repository.ProcessDefinitionRepository;
import com.wms.core.repository.ProcessStepDefinitionRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessStepDefinitionService {

    private final ProcessStepDefinitionRepository stepDefinitionRepository;
    private final LocationProcessConfigRepository locationProcessConfigRepository;
    private final LocationProcessStepConfigRepository locationProcessStepConfigRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ProcessStepDefinitionResponse> listAll() {
        return stepDefinitionRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcessStepDefinitionResponse getById(Long id) {
        ProcessStepDefinition entity = stepDefinitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Süreç adım tanımı bulunamadı", HttpStatus.NOT_FOUND));
        return toResponse(entity);
    }

    @Transactional
    public ProcessStepDefinitionResponse create(ProcessStepDefinitionRequest request) {
        ProcessDefinition procDef = processDefinitionRepository.findById(request.processDefinitionId())
                .orElseThrow(() -> new BusinessException("Süreç tanımı bulunamadı", HttpStatus.NOT_FOUND));

        ProcessStepDefinition stepDef = ProcessStepDefinition.builder()
                .processDefinition(procDef)
                .code(request.code())
                .name(request.name())
                .defaultSequence(request.defaultSequence())
                .build();
        stepDef.setActive(true);
        stepDef = stepDefinitionRepository.save(stepDef);

        // Sync: create passive step configs for all LocationProcessConfigs of this process
        List<LocationProcessConfig> locConfigs = locationProcessConfigRepository.findByProcessDefinitionId(procDef.getId());
        for (LocationProcessConfig lpc : locConfigs) {
            LocationProcessStepConfig stepConfig = LocationProcessStepConfig.builder()
                    .locationProcessConfig(lpc)
                    .processStepDefinition(stepDef)
                    .sequence(stepDef.getDefaultSequence())
                    .isMandatory(true)
                    .requiresApproval(false)
                    .errorStrategy(ErrorStrategy.BLOCK)
                    .build();
            stepConfig.setActive(false); // passive record
            stepConfig = locationProcessStepConfigRepository.save(stepConfig);

            // Publish ConfigChangeEvent for creation to evict cache and log audit
            eventPublisher.publishEvent(new ConfigChangeEvent(
                    this,
                    LocationProcessStepConfig.class.getSimpleName(),
                    stepConfig.getId(),
                    "CREATE",
                    List.of(new ConfigChangeEvent.FieldChange("active", null, "false")),
                    TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
            ));
        }

        // Also publish for the step definition itself
        eventPublisher.publishEvent(new ConfigChangeEvent(
                this,
                ProcessStepDefinition.class.getSimpleName(),
                stepDef.getId(),
                "CREATE",
                List.of(new ConfigChangeEvent.FieldChange("active", null, "true")),
                TenantContextHolder.getContext().map(TenantContext::userId).orElse(null)
        ));

        return toResponse(stepDef);
    }

    @Transactional
    public ProcessStepDefinitionResponse update(Long id, UpdateProcessStepDefinitionRequest request, boolean force) {
        ProcessStepDefinition stepDef = stepDefinitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Süreç adım tanımı bulunamadı", HttpStatus.NOT_FOUND));

        // If transitioning from active to passive, check if in use
        if (stepDef.isActive() && !request.isActive()) {
            long activeConfigsCount = locationProcessStepConfigRepository.countByProcessStepDefinitionIdAndIsActiveTrue(id);
            if (activeConfigsCount > 0 && !force) {
                throw new BusinessException("Bu süreç adımı kullanımda! Devam etmek için force parametresini true gönderin.",
                        HttpStatus.CONFLICT, "STEP_IN_USE_WARNING");
            }
        }

        stepDef.setName(request.name());
        stepDef.setDefaultSequence(request.defaultSequence());
        stepDef.setActive(request.isActive());
        stepDef = stepDefinitionRepository.save(stepDef);

        return toResponse(stepDef);
    }

    @Transactional
    public void delete(Long id, boolean force) {
        ProcessStepDefinition stepDef = stepDefinitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Süreç adım tanımı bulunamadı", HttpStatus.NOT_FOUND));

        long activeConfigsCount = locationProcessStepConfigRepository.countByProcessStepDefinitionIdAndIsActiveTrue(id);
        if (activeConfigsCount > 0 && !force) {
            throw new BusinessException("Bu süreç adımı kullanımda! Devam etmek için force parametresini true gönderin.",
                    HttpStatus.CONFLICT, "STEP_IN_USE_WARNING");
        }

        stepDef.setActive(false);
        stepDefinitionRepository.save(stepDef);
    }

    private ProcessStepDefinitionResponse toResponse(ProcessStepDefinition entity) {
        return new ProcessStepDefinitionResponse(
                entity.getId(),
                entity.getProcessDefinition().getId(),
                entity.getProcessDefinition().getCode(),
                entity.getCode(),
                entity.getName(),
                entity.getDefaultSequence(),
                entity.isActive()
        );
    }
}
