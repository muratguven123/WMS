package com.wms.core.aspect;

import com.wms.core.aspect.annotation.CheckWorkflowStep;
import com.wms.core.entity.ApprovalRequest;
import com.wms.core.entity.LocationProcessConfig;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.ProcessDefinition;
import com.wms.core.entity.ProcessStepDefinition;
import com.wms.core.entity.Role;
import com.wms.core.entity.UserAccess;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.exception.ApprovalRequiredException;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import com.wms.core.repository.UserAccessRepository;
import com.wms.core.security.TenantContext;
import com.wms.core.security.TenantContextHolder;
import com.wms.core.service.ApprovalRequestService;
import com.wms.core.service.QuarantineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowAspect} — {@code @CheckWorkflowStep} davranış testleri.
 *
 * <h3>Test tekniği</h3>
 * <p>Spring context yüklenmez. {@link AspectJProxyFactory} ile gerçek AspectJ
 * pointcut weaving yapılır: dummy hedef servis proxy'lenir, aspect'in
 * {@code @Around("@annotation(...)")} advice'ı gerçekten devreye girer.
 * Aspect bağımlılıkları (repository ve servisler) Mockito ile mocklanır.</p>
 *
 * <p><b>Not:</b> Onay senaryosu ({@code referenceIdParam = "receiptId"}) metot
 * parametre adlarının bytecode'da korunmasına dayanır (javac {@code -parameters}
 * bayrağı — Spring Boot parent bunu varsayılan açar). Bu test kırılırsa önce
 * compiler bayrağını kontrol edin.</p>
 *
 * <p>Sınıf LENIENT modda çalışır (senaryoya göre kullanılmayan entity stub'ları
 * var); kritik davranışlar {@code verify}/{@code executionCount} ile açıkça kanıtlanır.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowAspect — @CheckWorkflowStep davranışları")
class WorkflowAspectTest {

    private static final Long USER_ID           = 1L;
    private static final Long COMPANY_ID        = 1L;
    private static final Long LOCATION_ID       = 1L;
    private static final Long PROCESS_CONFIG_ID = 1L;
    private static final Long STEP_CONFIG_ID    = 1L;
    private static final Long MANAGER_ROLE_ID   = 10L;
    private static final Long PICKER_ROLE_ID    = 20L;
    private static final Long RECEIPT_ID        = 1L;

    @Mock private LocationProcessConfigRepository locationProcessConfigRepository;
    @Mock private LocationProcessStepConfigRepository locationProcessStepConfigRepository;
    @Mock private UserAccessRepository userAccessRepository;
    @Mock private ApprovalRequestService approvalRequestService;
    @Mock private QuarantineService quarantineService;

    @InjectMocks
    private WorkflowAspect workflowAspect;

    private ReceivingWorkflowService target;
    private ReceivingWorkflowService proxy;

    @BeforeEach
    void setUp() {
        // Aspect, tenant bilgisini ThreadLocal'den okur
        TenantContextHolder.setContext(new TenantContext(USER_ID, COMPANY_ID, LOCATION_ID));

        // Gerçek pointcut weaving: dummy servis + aspect → CGLIB proxy
        target = new ReceivingWorkflowService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(workflowAspect);
        proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // =====================================================================
    // Senaryo 1 — Başarılı akış: rol yetkili, onay gerekmez
    // =====================================================================

    @Test
    @DisplayName("Rol yetkili + onaysız adım → metot normal execute edilir")
    void authorizedRoleWithoutApproval_proceedsNormally() {
        stubStepConfig(MANAGER_ROLE_ID, false, ErrorStrategy.BLOCK);
        stubUserRole(MANAGER_ROLE_ID); // kullanıcının rolü gereken rolle eşleşiyor

        String result = proxy.completeQc();

        assertThat(result).isEqualTo("OK");
        assertThat(target.executionCount).isEqualTo(1); // joinPoint.proceed() çalıştı
        verify(userAccessRepository).findByUserIdAndCompanyId(USER_ID, COMPANY_ID);
        verifyNoInteractions(approvalRequestService, quarantineService);
    }

    // =====================================================================
    // Senaryo 2 — Yetkisiz rol: WarehouseManager gerekli, kullanıcı Picker
    // =====================================================================

    @Test
    @DisplayName("Gereken rol Manager, kullanıcı Picker → 403 WORKFLOW_ROLE_UNAUTHORIZED, metot çalışmaz")
    void unauthorizedRole_blocksExecutionWith403() {
        stubStepConfig(MANAGER_ROLE_ID, false, ErrorStrategy.BLOCK);
        stubUserRole(PICKER_ROLE_ID); // rol eşleşmiyor

        assertThatThrownBy(() -> proxy.completeQc())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("WORKFLOW_ROLE_UNAUTHORIZED");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        assertThat(target.executionCount).isZero(); // aspect araya girdi, proceed edilmedi
        verifyNoInteractions(approvalRequestService, quarantineService);
    }

    // =====================================================================
    // Senaryo 3 — Onay gereksinimi: işlem kesilir, ApprovalRequest açılır
    // =====================================================================

    @Test
    @DisplayName("requiresApproval=true → ApprovalRequest oluşturulur, işlem 202 ile kesilir")
    void requiresApproval_createsPendingRequestAndInterrupts() {
        stubStepConfig(null, true, ErrorStrategy.BLOCK);

        Long approvalId = 1L;
        ApprovalRequest approval = mock(ApprovalRequest.class);
        when(approval.getId()).thenReturn(approvalId);
        // createPendingRequest sözleşmesi kaydı PENDING_APPROVAL statüsüyle açar
        // (servisin kendi davranışı ApprovalRequestService testlerinde doğrulanır)
        when(approvalRequestService.createPendingRequest(STEP_CONFIG_ID, "RECEIPT", RECEIPT_ID, USER_ID))
                .thenReturn(approval);

        assertThatThrownBy(() -> proxy.completeQcWithApproval(RECEIPT_ID))
                .isInstanceOf(ApprovalRequiredException.class)
                .satisfies(ex -> assertThat(((ApprovalRequiredException) ex).getApprovalRequestId())
                        .isEqualTo(approvalId));

        // Doğru referansla onay talebi açıldı
        verify(approvalRequestService).createPendingRequest(STEP_CONFIG_ID, "RECEIPT", RECEIPT_ID, USER_ID);
        // Metot HİÇ çalışmadı — süreç onaya kadar askıda
        assertThat(target.executionCount).isZero();
        verifyNoInteractions(quarantineService);
    }

    // =====================================================================
    // Senaryo 4 — Hata stratejisi: ROUTE_TO_QUARANTINE
    // =====================================================================

    @Test
    @DisplayName("ROUTE_TO_QUARANTINE: metot hata fırlatır → hata yutulur, ürün karantinaya gider")
    void errorStrategyQuarantine_swallowsErrorAndRoutesToQuarantine() {
        stubStepConfig(null, false, ErrorStrategy.ROUTE_TO_QUARANTINE);

        // failingStep içerde IllegalStateException("Sensör hatası") fırlatır —
        // proxy üzerinden çağrı exception FIRLATMAMALI
        String result = proxy.failingStep();

        assertThat(result).isNull();                    // BYPASS benzeri null dönüş
        assertThat(target.executionCount).isEqualTo(1); // metot çalıştı, hata içeride yakalandı
        verify(quarantineService).routeToQuarantine(LOCATION_ID, STEP_CONFIG_ID, "Sensör hatası");
    }

    // =====================================================================
    // Tamamlayıcı — BLOCK ve BYPASS stratejileri (switch'in kalan kolları)
    // =====================================================================

    @Test
    @DisplayName("BLOCK: adım hatası 422 WORKFLOW_STEP_BLOCKED olarak yükseltilir")
    void errorStrategyBlock_rethrowsAsBusinessException() {
        stubStepConfig(null, false, ErrorStrategy.BLOCK);

        assertThatThrownBy(() -> proxy.failingStep())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("WORKFLOW_STEP_BLOCKED");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
        verifyNoInteractions(quarantineService);
    }

    @Test
    @DisplayName("BYPASS: adım hatası yutulur, karantinaya gidilmez")
    void errorStrategyBypass_swallowsErrorSilently() {
        stubStepConfig(null, false, ErrorStrategy.BYPASS);

        assertThat(proxy.failingStep()).isNull();
        assertThat(target.executionCount).isEqualTo(1);
        verifyNoInteractions(quarantineService, approvalRequestService);
    }

    // =====================================================================
    // Stub yardımcıları — entity grafiği Mockito mock'larıyla kurulur
    // =====================================================================

    /**
     * INBOUND/QC adım konfigürasyonunu verilen rol, onay ve hata stratejisiyle stublar.
     */
    private void stubStepConfig(Long responsibleRoleId, boolean requiresApproval, ErrorStrategy strategy) {
        ProcessDefinition processDefinition = mock(ProcessDefinition.class);
        when(processDefinition.getCode()).thenReturn("INBOUND");

        LocationProcessConfig processConfig = mock(LocationProcessConfig.class);
        when(processConfig.getProcessDefinition()).thenReturn(processDefinition);
        when(processConfig.getId()).thenReturn(PROCESS_CONFIG_ID);

        ProcessStepDefinition stepDefinition = mock(ProcessStepDefinition.class);
        when(stepDefinition.getCode()).thenReturn("QC");

        LocationProcessStepConfig stepConfig = mock(LocationProcessStepConfig.class);
        when(stepConfig.getProcessStepDefinition()).thenReturn(stepDefinition);
        when(stepConfig.getId()).thenReturn(STEP_CONFIG_ID);
        when(stepConfig.getResponsibleRoleId()).thenReturn(responsibleRoleId);
        when(stepConfig.isRequiresApproval()).thenReturn(requiresApproval);
        when(stepConfig.getErrorStrategy()).thenReturn(strategy);

        when(locationProcessConfigRepository.findActiveByLocationId(LOCATION_ID))
                .thenReturn(List.of(processConfig));
        when(locationProcessStepConfigRepository.findActiveStepsByConfigId(PROCESS_CONFIG_ID))
                .thenReturn(List.of(stepConfig));
    }

    /**
     * Kullanıcıya, şirket genelinde (location=null → tüm lokasyonlar) verilen rolü atar.
     */
    private void stubUserRole(Long roleId) {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);

        UserAccess access = mock(UserAccess.class);
        when(access.getLocation()).thenReturn(null);
        when(access.getRole()).thenReturn(role);

        when(userAccessRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(access));
    }

    // =====================================================================
    // Dummy hedef servis — @CheckWorkflowStep ile korunan örnek metotlar
    // =====================================================================

    static class ReceivingWorkflowService {

        int executionCount = 0;

        @CheckWorkflowStep(processCode = "INBOUND", stepCode = "QC")
        public String completeQc() {
            executionCount++;
            return "OK";
        }

        @CheckWorkflowStep(processCode = "INBOUND", stepCode = "QC",
                           referenceIdParam = "receiptId", referenceType = "RECEIPT")
        public String completeQcWithApproval(Long receiptId) {
            executionCount++;
            return "OK";
        }

        @CheckWorkflowStep(processCode = "INBOUND", stepCode = "QC")
        public String failingStep() {
            executionCount++;
            throw new IllegalStateException("Sensör hatası");
        }
    }
}
