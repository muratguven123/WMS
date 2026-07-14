package com.wms.core.aspect;

import com.wms.core.aspect.annotation.CheckWorkflowStep;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.exception.ApprovalRequiredException;
import com.wms.core.exception.BusinessException;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.QuarantineService;
import com.wms.core.service.WorkflowEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Optional;

/**
 * {@link CheckWorkflowStep} ile işaretlenen metodların çalışmadan önce
 * {@link WorkflowEnforcementService} üzerinden rol/onay kontrolü yapan AOP Aspect.
 * Hata stratejisi (BLOCK / BYPASS / ROUTE_TO_QUARANTINE) bu aspect'te uygulanır.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class WorkflowAspect {

    private final WorkflowEnforcementService workflowEnforcementService;
    private final QuarantineService quarantineService;

    @Around("@annotation(checkWorkflowStep)")
    public Object around(ProceedingJoinPoint joinPoint, CheckWorkflowStep checkWorkflowStep) throws Throwable {
        Long referenceId = extractReferenceId(joinPoint, checkWorkflowStep.referenceIdParam());

        Optional<LocationProcessStepConfig> stepConfigOpt = workflowEnforcementService.prepareStep(
                checkWorkflowStep.processCode(),
                checkWorkflowStep.stepCode(),
                checkWorkflowStep.referenceType(),
                referenceId);

        if (stepConfigOpt.isEmpty()) {
            return joinPoint.proceed();
        }

        Long locationId = TenantContextHolder.require().locationId();
        return executeWithErrorStrategy(joinPoint, stepConfigOpt.get(), locationId);
    }

    private Object executeWithErrorStrategy(
            ProceedingJoinPoint joinPoint,
            LocationProcessStepConfig stepConfig,
            Long locationId) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (ApprovalRequiredException | BusinessException knownEx) {
            throw knownEx;
        } catch (Exception ex) {
            ErrorStrategy strategy = stepConfig.getErrorStrategy();
            String stepCode = stepConfig.getProcessStepDefinition().getCode();

            log.error("[WorkflowAspect] Adım hatası. stepCode={} strategy={} hata={}",
                    stepCode, strategy, ex.getMessage(), ex);

            return switch (strategy) {
                case BLOCK -> throw new BusinessException(
                        "Süreç adımı başarısız oldu ve BLOCK stratejisi uygulandı. stepCode=" + stepCode,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "WORKFLOW_STEP_BLOCKED");
                case BYPASS -> {
                    log.warn("[WorkflowAspect] BYPASS: Adım hatası görmezden gelindi. stepCode={}", stepCode);
                    yield null;
                }
                case ROUTE_TO_QUARANTINE -> {
                    log.warn("[WorkflowAspect] ROUTE_TO_QUARANTINE: stepCode={} locationId={}",
                            stepCode, locationId);
                    quarantineService.routeToQuarantine(locationId, stepConfig.getId(), ex.getMessage());
                    yield null;
                }
            };
        }
    }

    private Long extractReferenceId(ProceedingJoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isBlank()) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                Object value = args[i];
                if (value instanceof Long uuid) {
                    return uuid;
                }
                if (value instanceof String str) {
                    return Long.parseLong(str);
                }
                log.warn("[WorkflowAspect] referenceIdParam '{}' Long değil, null kullanılıyor.", paramName);
                return null;
            }
        }

        log.warn("[WorkflowAspect] Metot parametresi bulunamadı: '{}'. Mevcut parametreler: {}",
                paramName, Arrays.stream(parameters).map(Parameter::getName).toList());
        return null;
    }
}
