package com.wms.core.aspect;

import com.wms.core.aspect.annotation.CheckWorkflowStep;
import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.UserAccess;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.exception.ApprovalRequiredException;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.ApprovalRequestService;
import com.wms.core.service.QuarantineService;
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

/**
 * {@link CheckWorkflowStep} ile işaretlenen metodların çalışmadan önce
 * üç katmanlı kontrol yapan AOP Aspect.
 *
 * <h3>Kontrol sırası</h3>
 * <ol>
 *   <li><b>Adım konfigürasyonu yükleme</b> — TenantContext'teki locationId,
 *       annotation'daki processCode ve stepCode ile DB'den aktif
 *       {@link LocationProcessStepConfig} çekilir.</li>
 *   <li><b>Rol yetki kontrolü</b> — {@code responsibleRoleId} tanımlanmışsa
 *       kullanıcının aktif lokasyon+şirketteki rolü kontrol edilir.
 *       Eşleşmiyorsa HTTP 403 {@link BusinessException} fırlatılır.</li>
 *   <li><b>Onay kontrolü</b> — {@code requiresApproval = true} ise
 *       {@link ApprovalRequest} kaydı oluşturulur, işlem proceed edilmez,
 *       {@link ApprovalRequiredException} (HTTP 202) fırlatılır.</li>
 *   <li><b>Hata stratejisi</b> — Metot çalışırken exception oluşursa
 *       {@code errorStrategy} değerine göre BLOCK / BYPASS / ROUTE_TO_QUARANTINE
 *       uygulanır.</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class WorkflowAspect {

    private final LocationProcessConfigRepository locationProcessConfigRepository;
    private final LocationProcessStepConfigRepository locationProcessStepConfigRepository;
    private final UserAccessRepository userAccessRepository;
    private final ApprovalRequestService approvalRequestService;
    private final QuarantineService quarantineService;

    // -----------------------------------------------------------------------
    // Pointcut + Around advice
    // -----------------------------------------------------------------------

    @Around("@annotation(checkWorkflowStep)")
    public Object around(ProceedingJoinPoint joinPoint, CheckWorkflowStep checkWorkflowStep) throws Throwable {

        var ctx = TenantContextHolder.require();
        Long locationId = ctx.locationId();
        Long userId     = ctx.userId();
        Long companyId  = ctx.companyId();

        // 1. Adım konfigürasyonunu yükle
        LocationProcessStepConfig stepConfig =
                resolveStepConfig(locationId, checkWorkflowStep.processCode(), checkWorkflowStep.stepCode());

        // 2. Rol yetki kontrolü
        checkRolePermission(stepConfig, userId, companyId, locationId);

        // 3. Onay kontrolü — approve gerektiriyorsa işlemi askıya al
        if (stepConfig.isRequiresApproval()) {
            Long referenceId = extractReferenceId(joinPoint, checkWorkflowStep.referenceIdParam());
            if (referenceId == null) {
                throw new BusinessException(
                        "Onay gerektiren adımlar için referenceIdParam zorunludur.",
                        HttpStatus.BAD_REQUEST,
                        "APPROVAL_REFERENCE_REQUIRED");
            }
            ApprovalRequest request = approvalRequestService.createPendingRequest(
                    stepConfig.getId(),
                    checkWorkflowStep.referenceType(),
                    referenceId,
                    userId);

            log.info("[WorkflowAspect] Onay talebi oluşturuldu. approvalRequestId={} stepCode={} userId={}",
                    request.getId(), checkWorkflowStep.stepCode(), userId);

            throw new ApprovalRequiredException(request.getId());
        }

        // 4. Metodu çalıştır; exception'ı errorStrategy'e göre yönet
        return executeWithErrorStrategy(joinPoint, stepConfig, locationId);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * TenantContext.locationId + processCode + stepCode ile adım konfigürasyonunu getirir.
     */
    private LocationProcessStepConfig resolveStepConfig(Long locationId,
                                                         String processCode,
                                                         String stepCode) {
        return locationProcessConfigRepository
                .findActiveByLocationId(locationId)
                .stream()
                .filter(lpc -> lpc.getProcessDefinition().getCode().equals(processCode))
                .findFirst()
                .flatMap(config -> locationProcessStepConfigRepository
                        .findActiveStepsByConfigId(config.getId())
                        .stream()
                        .filter(sc -> sc.getProcessStepDefinition().getCode().equals(stepCode))
                        .findFirst())
                .orElseThrow(() -> new BusinessException(
                        "Aktif süreç adımı konfigürasyonu bulunamadı. locationId=%s processCode=%s stepCode=%s"
                                .formatted(locationId, processCode, stepCode),
                        HttpStatus.NOT_FOUND,
                        "WORKFLOW_STEP_CONFIG_NOT_FOUND"));
    }

    /**
     * Adımda {@code responsibleRoleId} tanımlıysa kullanıcının rolünü karşılaştırır.
     */
    private void checkRolePermission(LocationProcessStepConfig stepConfig,
                                      Long userId, Long companyId, Long locationId) {
        Long requiredRoleId = stepConfig.getResponsibleRoleId();
        if (requiredRoleId == null) {
            return; // Kısıtlama yok — herhangi yetkili kullanabilir
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
            log.warn("[WorkflowAspect] Yetkisiz erişim. userId={} requiredRoleId={} stepCode={}",
                    userId, requiredRoleId, stepConfig.getProcessStepDefinition().getCode());
            throw new BusinessException(
                    "Bu süreç adımını yürütmek için gerekli role sahip değilsiniz.",
                    HttpStatus.FORBIDDEN,
                    "WORKFLOW_ROLE_UNAUTHORIZED");
        }
    }

    /**
     * Metodu çalıştırır; exception oluşursa {@code errorStrategy}'e göre karar verir.
     */
    private Object executeWithErrorStrategy(ProceedingJoinPoint joinPoint,
                                             LocationProcessStepConfig stepConfig,
                                             Long locationId) throws Throwable {
        try {
            return joinPoint.proceed();

        } catch (ApprovalRequiredException | BusinessException knownEx) {
            // Bilinen iş exception'ları errorStrategy'den bağımsız fırlatılır
            throw knownEx;

        } catch (Exception ex) {
            ErrorStrategy strategy = stepConfig.getErrorStrategy();
            String stepCode = stepConfig.getProcessStepDefinition().getCode();

            log.error("[WorkflowAspect] Adım hatası. stepCode={} strategy={} hata={}",
                    stepCode, strategy, ex.getMessage(), ex);

            return switch (strategy) {

                case BLOCK -> {
                    // İşlemi tamamen durdur
                    throw new BusinessException(
                            "Süreç adımı başarısız oldu ve BLOCK stratejisi uygulandı. stepCode=" + stepCode,
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "WORKFLOW_STEP_BLOCKED");
                }

                case BYPASS -> {
                    // Uyarı logla, normal akışta devam et (null dön)
                    log.warn("[WorkflowAspect] BYPASS: Adım hatası görmezden gelindi. stepCode={}", stepCode);
                    yield null;
                }

                case ROUTE_TO_QUARANTINE -> {
                    // Ürünü karantinaya yönlendir, adımı başarılı say
                    log.warn("[WorkflowAspect] ROUTE_TO_QUARANTINE: stepCode={} locationId={}",
                            stepCode, locationId);
                    quarantineService.routeToQuarantine(locationId, stepConfig.getId(), ex.getMessage());
                    yield null;
                }
            };
        }
    }

    /**
     * Metot parametreleri içinden {@code paramName} adındaki Long parametreyi bulur.
     * annotation'da {@code referenceIdParam} boşsa {@code null} döner.
     */
    private Long extractReferenceId(ProceedingJoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isBlank()) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters   = signature.getMethod().getParameters();
        Object[]    args         = joinPoint.getArgs();

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
