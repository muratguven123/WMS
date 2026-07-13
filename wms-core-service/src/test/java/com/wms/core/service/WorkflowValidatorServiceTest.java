package com.wms.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.core.dto.workflow.ProcessStepDto;
import com.wms.core.dto.workflow.WorkflowStepCacheEntry;
import com.wms.core.entity.LocationProcessConfig;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.entity.ProcessDefinition;
import com.wms.core.entity.ProcessStepDefinition;
import com.wms.core.entity.enums.ErrorStrategy;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorkflowValidatorService birim testleri.
 *
 * <p>Her senaryo izole edilmiştir; Redis ve Repository bağımlılıkları mock'lanır.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowValidatorService Tests")
class WorkflowValidatorServiceTest {

    @Mock
    private LocationProcessConfigRepository locationProcessConfigRepository;

    @Mock
    private LocationProcessStepConfigRepository locationProcessStepConfigRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private WorkflowValidatorService service;

    private final ObjectMapper redisObjectMapper = new ObjectMapper();

    private static final Long LOCATION_ID  = 1L;
    private static final String PROCESS_CODE = "INBOUND";

    @BeforeEach
    void setUp() throws Exception {
        var field = WorkflowValidatorService.class.getDeclaredField("redisObjectMapper");
        field.setAccessible(true);
        field.set(service, redisObjectMapper);
    }

    private void stubRedisOps() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // -----------------------------------------------------------------------
    // Helper: mock cache miss → DB load
    // -----------------------------------------------------------------------

    private void givenCacheMissAndDbSteps(List<LocationProcessStepConfig> dbSteps) {
        stubRedisOps();
        when(valueOperations.get(anyString())).thenReturn(null);

        ProcessDefinition processDef = new ProcessDefinition();
        processDef.setCode(PROCESS_CODE);

        LocationProcessConfig config = new LocationProcessConfig();
        config.setId(1L);
        config.setLocationId(LOCATION_ID);
        config.setProcessDefinition(processDef);

        when(locationProcessConfigRepository.findActiveByLocationId(LOCATION_ID))
                .thenReturn(List.of(config));
        when(locationProcessStepConfigRepository.findActiveStepsByConfigId(config.getId()))
                .thenReturn(dbSteps);
    }

    private LocationProcessStepConfig stepConfig(String code, int seq, boolean mandatory) {
        ProcessDefinition pd = new ProcessDefinition();
        pd.setCode(PROCESS_CODE);

        ProcessStepDefinition psd = new ProcessStepDefinition();
        psd.setCode(code);
        psd.setName(code + "_NAME");
        psd.setDefaultSequence(seq);
        psd.setProcessDefinition(pd);

        LocationProcessStepConfig sc = new LocationProcessStepConfig();
        sc.setId(1L);
        sc.setProcessStepDefinition(psd);
        sc.setSequence(seq);
        sc.setMandatory(mandatory);
        sc.setRequiresApproval(false);
        sc.setErrorStrategy(ErrorStrategy.BLOCK);
        sc.setActive(true);
        return sc;
    }

    // -----------------------------------------------------------------------
    // Cache hit testi
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Cache Senaryoları")
    class CacheTests {

        @Test
        @DisplayName("Cache hit: DB'ye hiç gidilmemeli")
        void whenCacheHit_shouldNotQueryDatabase() {
            stubRedisOps();
            List<WorkflowStepCacheEntry> cached = List.of(
                    WorkflowStepCacheEntry.builder().stepCode("RECEIVING").sequence(10).mandatory(true).errorStrategy(ErrorStrategy.BLOCK).build(),
                    WorkflowStepCacheEntry.builder().stepCode("QC").sequence(20).mandatory(true).errorStrategy(ErrorStrategy.BLOCK).build()
            );
            when(valueOperations.get(anyString())).thenReturn(cached);

            // Act
            ProcessStepDto result = service.determineNextStep(LOCATION_ID, PROCESS_CODE, "RECEIVING");

            // Assert
            assertThat(result.stepCode()).isEqualTo("QC");
            verifyNoInteractions(locationProcessConfigRepository, locationProcessStepConfigRepository);
        }

        @Test
        @DisplayName("Cache miss: DB sorgulanmalı ve sonuç cache'e yazılmalı")
        void whenCacheMiss_shouldLoadFromDbAndWriteCache() {
            stubRedisOps();
            givenCacheMissAndDbSteps(List.of(
                    stepConfig("RECEIVING", 10, true),
                    stepConfig("QC", 20, true)
            ));

            // Act
            ProcessStepDto result = service.determineNextStep(LOCATION_ID, PROCESS_CODE, "RECEIVING");

            // Assert
            assertThat(result.stepCode()).isEqualTo("QC");
            verify(valueOperations).set(anyString(), any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // Zorunlu adım senaryoları
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Zorunlu Adım (mandatory=true) Senaryoları")
    class MandatoryStepTests {

        @Test
        @DisplayName("Sıradaki adım mandatory ise direkt dönmeli")
        void nextStep_isMandatory_shouldReturnDirectly() {
            givenCacheMissAndDbSteps(List.of(
                    stepConfig("RECEIVING", 10, true),
                    stepConfig("QC",        20, true),
                    stepConfig("PUTAWAY",   30, true)
            ));

            ProcessStepDto result = service.determineNextStep(LOCATION_ID, PROCESS_CODE, "RECEIVING");

            assertThat(result.stepCode()).isEqualTo("QC");
            assertThat(result.mandatory()).isTrue();
            assertThat(result.processCompleted()).isFalse();
        }

        @Test
        @DisplayName("Son adım tamamlandığında processCompleted=true dönmeli")
        void whenLastStepCompleted_shouldReturnCompleted() {
            givenCacheMissAndDbSteps(List.of(
                    stepConfig("RECEIVING", 10, true),
                    stepConfig("PUTAWAY",   20, true)
            ));

            ProcessStepDto result = service.determineNextStep(LOCATION_ID, PROCESS_CODE, "PUTAWAY");

            assertThat(result.processCompleted()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Opsiyonel adım atlama senaryoları
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Opsiyonel Adım Atlama (skipOptional=true) Senaryoları")
    class SkipOptionalTests {

        @Test
        @DisplayName("skipOptional=false: opsiyonel adım atlanmamalı, direkt dönmeli")
        void skipOptionalFalse_shouldReturnOptionalStep() {
            givenCacheMissAndDbSteps(List.of(
                    stepConfig("RECEIVING",      10, true),
                    stepConfig("SERIAL_CONTROL", 20, false), // opsiyonel
                    stepConfig("PUTAWAY",        30, true)
            ));

            ProcessStepDto result = service.determineNextStep(LOCATION_ID, PROCESS_CODE, "RECEIVING", false);

            assertThat(result.stepCode()).isEqualTo("SERIAL_CONTROL");
            assertThat(result.mandatory()).isFalse();
        }

        @Test
        @DisplayName("skipOptional=true: opsiyonel adım atlanmalı, ilk mandatory döndürülmeli")
        void skipOptionalTrue_shouldSkipAndReturnNextMandatory() {
            givenCacheMissAndDbSteps(List.of(
                    stepConfig("RECEIVING",      10, true),
                    stepConfig("SERIAL_CONTROL", 20, false), // atlanacak
                    stepConfig("CUSTOMS",        25, false), // atlanacak
                    stepConfig("PUTAWAY",        30, true)   // döndürülecek
            ));

            ProcessStepDto result = service.determineNextStep(LOCATION_ID, PROCESS_CODE, "RECEIVING", true);

            assertThat(result.stepCode()).isEqualTo("PUTAWAY");
            assertThat(result.mandatory()).isTrue();
        }

        @Test
        @DisplayName("skipOptional=true ve tüm kalan adımlar opsiyonel ise processCompleted=true")
        void skipOptionalTrue_allRemainingOptional_shouldReturnCompleted() {
            givenCacheMissAndDbSteps(List.of(
                    stepConfig("RECEIVING",      10, true),
                    stepConfig("SERIAL_CONTROL", 20, false),
                    stepConfig("PHOTO_CAPTURE",  30, false)
            ));

            ProcessStepDto result = service.determineNextStep(LOCATION_ID, PROCESS_CODE, "RECEIVING", true);

            assertThat(result.processCompleted()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Hata senaryoları
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Hata Senaryoları")
    class ErrorTests {

        @Test
        @DisplayName("Geçersiz stepCode girilirse BusinessException fırlatılmalı")
        void invalidStepCode_shouldThrowBusinessException() {
            givenCacheMissAndDbSteps(List.of(
                    stepConfig("RECEIVING", 10, true),
                    stepConfig("QC",        20, true)
            ));

            assertThatThrownBy(() ->
                    service.determineNextStep(LOCATION_ID, PROCESS_CODE, "NONEXISTENT_STEP"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("NONEXISTENT_STEP");
        }

        @Test
        @DisplayName("Lokasyon için aktif konfigürasyon yoksa BusinessException fırlatılmalı")
        void noConfigForLocation_shouldThrowBusinessException() {
            stubRedisOps();
            when(valueOperations.get(anyString())).thenReturn(null);
            when(locationProcessConfigRepository.findActiveByLocationId(LOCATION_ID))
                    .thenReturn(List.of());

            assertThatThrownBy(() ->
                    service.determineNextStep(LOCATION_ID, PROCESS_CODE, "RECEIVING"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assert "WORKFLOW_CONFIG_NOT_FOUND".equals(be.getErrorCode());
                    });
        }
    }

    // -----------------------------------------------------------------------
    // Cache eviction testi
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("evictCache doğru key ile Redis delete çağırmalı")
    void evictCache_shouldDeleteCorrectKey() {
        String expectedKey = "workflow:" + LOCATION_ID + ":" + PROCESS_CODE;

        service.evictCache(LOCATION_ID, PROCESS_CODE);

        verify(redisTemplate).delete(expectedKey);
    }
}
