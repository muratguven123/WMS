package com.wms.core.service;

import com.wms.core.dto.workflow.WorkflowEnforceRequest;
import com.wms.core.dto.workflow.WorkflowEnforceResponse;
import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.exception.ApprovalRequiredException;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Lokasyon bazlı süreç adımı zorlaması — rol ve onay ön kontrolleri.
 * {@link com.wms.core.aspect.WorkflowAspect} ve REST {@code /api/workflow/enforce} bu servisi kullanır.
 * Yerel hata stratejisi (BLOCK/BYPASS/QUARANTINE) aspect içinde kalır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEnforcementService {

    private final LocationProcessConfigRepository locationProcessConfigRepository;
    private final LocationProcessStepConfigRepository locationProcessStepConfigRepository;
    private final UserAccessRepository userAccessRepository;
    private final ApprovalRequestService approvalRequestService;

    /**
     * Uzak servisler için ön kontrol: adım aktif mi, rol yetkili mi, onay gerekli mi.
     * Onay gerektiğinde exception yerine {@link WorkflowEnforceResponse.Decision#APPROVAL_REQUIRED} döner.
     */
    public WorkflowEnforceResponse enforce(WorkflowEnforceRequest request) {
        var ctx = TenantContextHolder.require();
        Long locationId = ctx.locationId();
        Long userId = ctx.userId();
        Long companyId = ctx.companyId();

        Optional<LocationProcessStepConfig> stepConfigOpt =
                resolveStepConfig(locationId, request.processCode(), request.stepCode());

        if (stepConfigOpt.isEmpty()) {
            log.info("[WorkflowEnforce] Adım pasif/tanımsız; bypass. processCode={} stepCode={}",
                    request.processCode(), request.stepCode());
            return WorkflowEnforceResponse.bypassed(request.processCode(), request.stepCode());
        }

        LocationProcessStepConfig stepConfig = stepConfigOpt.get();
        checkRolePermission(stepConfig, userId, companyId, locationId);

        if (stepConfig.isRequiresApproval()) {
            if (request.referenceId() == null) {
                throw new BusinessException(
                        "Onay gerektiren adımlar için referenceId zorunludur.",
                        HttpStatus.BAD_REQUEST,
                        "APPROVAL_REFERENCE_REQUIRED");
            }
            String referenceType = request.referenceType() != null && !request.referenceType().isBlank()
                    ? request.referenceType()
                    : "UNKNOWN";
            ApprovalRequest approval = approvalRequestService.createPendingRequest(
                    stepConfig.getId(),
                    referenceType,
                    request.referenceId(),
                    userId);

            log.info("[WorkflowEnforce] Onay talebi oluşturuldu. approvalRequestId={} stepCode={}",
                    approval.getId(), request.stepCode());
            return WorkflowEnforceResponse.approvalRequired(
                    approval.getId(), stepConfig.getId(), request.processCode(), request.stepCode());
        }

        return WorkflowEnforceResponse.allowed(
                stepConfig.getId(), request.processCode(), request.stepCode());
    }

    /**
     * Yerel aspect için: boş = bypass; dolu = adım aktif ve yetkili (onay yok).
     * Onay gerektiğinde exception fırlatır.
     */
    public Optional<LocationProcessStepConfig> prepareStep(
            String processCode,
            String stepCode,
            String referenceType,
            Long referenceId) {

        var ctx = TenantContextHolder.require();
        Long locationId = ctx.locationId();
        Long userId = ctx.userId();
        Long companyId = ctx.companyId();

        Optional<LocationProcessStepConfig> stepConfigOpt =
                resolveStepConfig(locationId, processCode, stepCode);

        if (stepConfigOpt.isEmpty()) {
            log.info("[WorkflowEnforce] Adım pasif/tanımsız; bypass. processCode={} stepCode={}",
                    processCode, stepCode);
            return Optional.empty();
        }

        LocationProcessStepConfig stepConfig = stepConfigOpt.get();
        checkRolePermission(stepConfig, userId, companyId, locationId);

        if (stepConfig.isRequiresApproval()) {
            if (referenceId == null) {
                throw new BusinessException(
                        "Onay gerektiren adımlar için referenceId zorunludur.",
                        HttpStatus.BAD_REQUEST,
                        "APPROVAL_REFERENCE_REQUIRED");
            }
            String type = referenceType != null && !referenceType.isBlank() ? referenceType : "UNKNOWN";
            ApprovalRequest approval = approvalRequestService.createPendingRequest(
                    stepConfig.getId(), type, referenceId, userId);

            log.info("[WorkflowEnforce] Onay talebi oluşturuldu. approvalRequestId={} stepCode={} userId={}",
                    approval.getId(), stepCode, userId);
            throw new ApprovalRequiredException(approval.getId());
        }

        return Optional.of(stepConfig);
    }

    public Optional<LocationProcessStepConfig> resolveStepConfig(
            Long locationId, String processCode, String stepCode) {
        return locationProcessConfigRepository
                .findActiveByLocationId(locationId)
                .stream()
                .filter(lpc -> lpc.getProcessDefinition().getCode().equals(processCode))
                .findFirst()
                .flatMap(config -> locationProcessStepConfigRepository
                        .findActiveStepsByConfigId(config.getId())
                        .stream()
                        .filter(sc -> sc.getProcessStepDefinition().getCode().equals(stepCode))
                        .findFirst());
    }

    public void checkRolePermission(
            LocationProcessStepConfig stepConfig, Long userId, Long companyId, Long locationId) {
        Long requiredRoleId = stepConfig.getResponsibleRoleId();
        if (requiredRoleId == null) {
            return;
        }

        boolean hasRole = userAccessRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .stream()
                .anyMatch(ua -> {
                    boolean locationMatch = ua.getLocation() == null
                            || ua.getLocation().getId().equals(locationId);
                    boolean roleMatch = ua.getRole().getId().equals(requiredRoleId);
                    return locationMatch && roleMatch;
                });

        if (!hasRole) {
            log.warn("[WorkflowEnforce] Yetkisiz erişim. userId={} requiredRoleId={} stepCode={}",
                    userId, requiredRoleId, stepConfig.getProcessStepDefinition().getCode());
            throw new BusinessException(
                    "Bu süreç adımını yürütmek için gerekli role sahip değilsiniz.",
                    HttpStatus.FORBIDDEN,
                    "WORKFLOW_ROLE_UNAUTHORIZED");
        }
    }
}
