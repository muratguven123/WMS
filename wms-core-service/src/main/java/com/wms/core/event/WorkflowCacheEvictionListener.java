package com.wms.core.event;

import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import com.wms.core.service.WorkflowValidatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Konfigürasyon değişikliğinde workflow Redis cache'ini temizler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowCacheEvictionListener {

    private final LocationProcessStepConfigRepository stepConfigRepository;
    private final WorkflowValidatorService workflowValidatorService;

    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        if (!LocationProcessStepConfig.class.getSimpleName().equals(event.getEntityName())) {
            return;
        }

        stepConfigRepository.findWithProcessById(event.getEntityId()).ifPresent(stepConfig -> {
            var lpc = stepConfig.getLocationProcessConfig();
            String processCode = lpc.getProcessDefinition().getCode();
            workflowValidatorService.evictCache(lpc.getLocationId(), processCode);
            log.info("[Workflow] Cache invalidated after config change. locationId={} processCode={}",
                    lpc.getLocationId(), processCode);
        });
    }
}
