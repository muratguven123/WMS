package com.wms.core.service;

import com.wms.core.dto.workflow.StepConfigResponse;
import com.wms.core.dto.workflow.UpdateStepConfigRequest;
import com.wms.core.entity.LocationProcessConfig;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lokasyon süreç adımı konfigürasyonu yönetimi.
 *
 * <p>Güncellemeler {@link com.wms.core.entity.listener.LocationProcessStepConfigAuditListener}
 * ve {@link com.wms.core.event.WorkflowCacheEvictionListener} tarafından otomatik izlenir.</p>
 */
@Service
@RequiredArgsConstructor
public class ProcessConfigService {

    private final LocationProcessStepConfigRepository stepConfigRepository;
    private final LocationProcessConfigRepository locationProcessConfigRepository;

    @Transactional(readOnly = true)
    public List<StepConfigResponse> listSteps(Long locationId, String processCode) {
        List<LocationProcessConfig> configs =
                locationProcessConfigRepository.findActiveByLocationId(locationId);

        List<StepConfigResponse> result = new ArrayList<>();
        for (LocationProcessConfig config : configs) {
            String code = config.getProcessDefinition().getCode();
            if (processCode != null && !processCode.isBlank()
                    && !processCode.equalsIgnoreCase(code)) {
                continue;
            }
            stepConfigRepository.findActiveStepsByConfigId(config.getId()).stream()
                    .map(step -> toResponse(step, code))
                    .forEach(result::add);
        }
        result.sort(Comparator.comparingInt(StepConfigResponse::sequence));
        return result;
    }

    @Transactional(readOnly = true)
    public LocationProcessStepConfig getStepConfig(Long stepConfigId) {
        return stepConfigRepository.findById(stepConfigId)
                .orElseThrow(() -> new BusinessException(
                        "Adım konfigürasyonu bulunamadı. id=" + stepConfigId,
                        HttpStatus.NOT_FOUND,
                        "STEP_CONFIG_NOT_FOUND"));
    }

    @Transactional
    public LocationProcessStepConfig updateStepConfig(Long stepConfigId, UpdateStepConfigRequest request) {
        LocationProcessStepConfig config = getStepConfig(stepConfigId);

        if (request.sequence() != null) {
            int newSeq = request.sequence();
            Long lpcId = config.getLocationProcessConfig().getId();
            if (stepConfigRepository.existsByLocationProcessConfigIdAndSequenceAndIdNot(
                    lpcId, newSeq, stepConfigId)) {
                throw new BusinessException(
                        "Bu sıra numarası bu akışta zaten kullanılıyor: " + newSeq,
                        HttpStatus.CONFLICT,
                        "STEP_CONFIG_SEQUENCE_CONFLICT");
            }
            config.setSequence(newSeq);
        }
        if (request.mandatory() != null) {
            config.setMandatory(request.mandatory());
        }
        if (request.active() != null) {
            config.setActive(request.active());
        }
        if (request.requiresApproval() != null) {
            config.setRequiresApproval(request.requiresApproval());
        }
        if (request.responsibleRoleId() != null) {
            config.setResponsibleRoleId(request.responsibleRoleId());
        }
        if (request.errorStrategy() != null) {
            config.setErrorStrategy(request.errorStrategy());
        }

        return stepConfigRepository.save(config);
    }

    private static StepConfigResponse toResponse(LocationProcessStepConfig step, String processCode) {
        var def = step.getProcessStepDefinition();
        return new StepConfigResponse(
                step.getId(),
                def != null ? def.getCode() : null,
                def != null ? def.getName() : null,
                step.getSequence(),
                step.isMandatory(),
                step.isActive(),
                step.isRequiresApproval(),
                step.getResponsibleRoleId(),
                processCode);
    }
}
