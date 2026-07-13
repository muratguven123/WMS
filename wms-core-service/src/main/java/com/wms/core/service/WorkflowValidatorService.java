package com.wms.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.core.dto.workflow.ProcessStepDto;
import com.wms.core.dto.workflow.WorkflowStepCacheEntry;
import com.wms.core.entity.LocationProcessStepConfig;
import com.wms.core.exception.BusinessException;
import com.wms.core.repository.LocationProcessConfigRepository;
import com.wms.core.repository.LocationProcessStepConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Lokasyon bazlı parametrik iş akışı doğrulama motoru.
 *
 * <p><b>Algoritma özeti:</b>
 * <ol>
 *   <li>Adım listesini Redis cache'den oku; cache miss ise DB'den yükle ve cache'e yaz.</li>
 *   <li>Adımları {@code sequence} değerine göre sırala.</li>
 *   <li>{@code currentStepCode} sonrasındaki ilk aktif adımı bul.</li>
 *   <li>Bulunan adım zorunlu ise (mandatory) direkt dön.</li>
 *   <li>{@code skipOptional = true} ise opsiyonel adımları atla, bir sonraki zorunlu/aktif adımı dön.</li>
 *   <li>Listede başka aktif adım kalmadıysa {@link ProcessStepDto#completed()} dön.</li>
 * </ol>
 * </p>
 *
 * <p><b>Redis key deseni:</b> {@code workflow:{locationId}:{processCode}}</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowValidatorService {

    private static final String CACHE_KEY_PREFIX = "workflow";
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final LocationProcessConfigRepository locationProcessConfigRepository;
    private final LocationProcessStepConfigRepository locationProcessStepConfigRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper redisObjectMapper;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Mevcut adım tamamlandığında bir sonraki aktif adımı belirler.
     * Opsiyonel adımlar atlanmaz; caller'a dönerek kullanıcının kararı beklenir.
     *
     * @param locationId      Aktif lokasyon Long'si
     * @param processCode     Süreç kodu — örn: INBOUND, OUTBOUND
     * @param currentStepCode Tamamlanan adımın kodu — örn: QC
     * @return Bir sonraki adım ya da sürecin tamamlandığını belirten {@link ProcessStepDto}
     */
    @Transactional(readOnly = true)
    public ProcessStepDto determineNextStep(Long locationId, String processCode, String currentStepCode) {
        return determineNextStep(locationId, processCode, currentStepCode, false);
    }

    /**
     * Bir sonraki adımı belirler; {@code skipOptional = true} ise opsiyonel adımları atlayarak
     * bir sonraki zorunlu veya kalan son aktif adımı döner.
     *
     * @param locationId      Aktif lokasyon Long'si
     * @param processCode     Süreç kodu — örn: INBOUND
     * @param currentStepCode Tamamlanan adımın kodu
     * @param skipOptional    true ise mandatory=false adımlar atlanır
     * @return Bir sonraki adım ya da {@link ProcessStepDto#completed()}
     */
    @Transactional(readOnly = true)
    public ProcessStepDto determineNextStep(Long locationId,
                                            String processCode,
                                            String currentStepCode,
                                            boolean skipOptional) {

        List<WorkflowStepCacheEntry> steps = loadSteps(locationId, processCode);

        // Mevcut adımın index'ini bul
        int currentIndex = findCurrentIndex(steps, currentStepCode);

        // Mevcut adımdan sonraki adayları al
        List<WorkflowStepCacheEntry> remaining = steps.subList(currentIndex + 1, steps.size());

        if (remaining.isEmpty()) {
            log.info("[Workflow] Süreç tamamlandı. locationId={} processCode={}", locationId, processCode);
            return ProcessStepDto.completed();
        }

        if (!skipOptional) {
            // İlk aktif adımı dön (zorunlu olup olmadığına bakılmaksızın)
            WorkflowStepCacheEntry next = remaining.get(0);
            log.debug("[Workflow] Sonraki adım: {} (mandatory={}) locationId={} processCode={}",
                    next.getStepCode(), next.isMandatory(), locationId, processCode);
            return toDto(next);
        }

        // skipOptional = true: opsiyonelleri atla, ilk mandatory'yi dön
        return remaining.stream()
                .filter(WorkflowStepCacheEntry::isMandatory)
                .findFirst()
                .map(step -> {
                    log.debug("[Workflow] Opsiyoneller atlandı, zorunlu adım: {} locationId={} processCode={}",
                            step.getStepCode(), locationId, processCode);
                    return toDto(step);
                })
                // Zorunlu adım da yoksa → kalan son aktif adımı dön ya da completed
                .orElseGet(() -> {
                    // skipOptional modunda tüm kalan opsiyoneller atlanabilir
                    log.info("[Workflow] Kalan tüm adımlar opsiyonel, süreç tamamlandı. locationId={} processCode={}",
                            locationId, processCode);
                    return ProcessStepDto.completed();
                });
    }

    /**
     * Belirli bir lokasyon + süreç konfigürasyonunun cache'ini geçersiz kılar.
     * Konfigürasyon güncellendiğinde çağrılmalıdır.
     */
    public void evictCache(Long locationId, String processCode) {
        String key = buildCacheKey(locationId, processCode);
        redisTemplate.delete(key);
        log.info("[Workflow] Cache temizlendi. key={}", key);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Adımları önce Redis'ten dener; bulamazsa DB'den yükleyip cache'e yazar.
     */
    private List<WorkflowStepCacheEntry> loadSteps(Long locationId, String processCode) {
        String key = buildCacheKey(locationId, processCode);

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            List<WorkflowStepCacheEntry> entries = redisObjectMapper.convertValue(
                    cached, new TypeReference<List<WorkflowStepCacheEntry>>() {});
            log.debug("[Workflow] Cache hit. key={} stepCount={}", key, entries.size());
            return entries;
        }

        log.debug("[Workflow] Cache miss, DB'den yükleniyor. key={}", key);
        List<WorkflowStepCacheEntry> steps = loadFromDatabase(locationId, processCode);

        redisTemplate.opsForValue().set(key, steps, CACHE_TTL);
        return steps;
    }

    /**
     * DB'den lokasyon-süreç konfigürasyonunu ve adımlarını çeker;
     * sequence'a göre sıralı {@link WorkflowStepCacheEntry} listesi döner.
     */
    private List<WorkflowStepCacheEntry> loadFromDatabase(Long locationId, String processCode) {
        var config = locationProcessConfigRepository
                .findActiveByLocationId(locationId)
                .stream()
                .filter(lpc -> lpc.getProcessDefinition().getCode().equals(processCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Lokasyon için aktif süreç konfigürasyonu bulunamadı. locationId=%s processCode=%s"
                                .formatted(locationId, processCode),
                        HttpStatus.NOT_FOUND,
                        "WORKFLOW_CONFIG_NOT_FOUND"));

        List<LocationProcessStepConfig> stepConfigs =
                locationProcessStepConfigRepository.findActiveStepsByConfigId(config.getId());

        if (stepConfigs.isEmpty()) {
            throw new BusinessException(
                    "Süreç konfigürasyonu için aktif adım bulunamadı. configId=%s".formatted(config.getId()),
                    HttpStatus.NOT_FOUND,
                    "WORKFLOW_STEPS_NOT_FOUND");
        }

        return stepConfigs.stream()
                .map(sc -> WorkflowStepCacheEntry.builder()
                        .stepConfigId(sc.getId())
                        .stepCode(sc.getProcessStepDefinition().getCode())
                        .stepName(sc.getProcessStepDefinition().getName())
                        .sequence(sc.getSequence())
                        .mandatory(sc.isMandatory())
                        .requiresApproval(sc.isRequiresApproval())
                        .errorStrategy(sc.getErrorStrategy())
                        .responsibleRoleId(sc.getResponsibleRoleId())
                        .build())
                .toList(); // findActiveStepsByConfigId zaten sequence ASC sıralar
    }

    /**
     * Listede {@code stepCode}'u arar; bulamazsa {@link BusinessException} fırlatır.
     */
    private int findCurrentIndex(List<WorkflowStepCacheEntry> steps, String stepCode) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStepCode().equals(stepCode)) {
                return i;
            }
        }
        throw new BusinessException(
                "Süreç içinde adım bulunamadı: stepCode=%s".formatted(stepCode),
                HttpStatus.BAD_REQUEST,
                "WORKFLOW_STEP_NOT_FOUND");
    }

    private ProcessStepDto toDto(WorkflowStepCacheEntry entry) {
        return ProcessStepDto.builder()
                .stepCode(entry.getStepCode())
                .stepName(entry.getStepName())
                .sequence(entry.getSequence())
                .mandatory(entry.isMandatory())
                .requiresApproval(entry.isRequiresApproval())
                .errorStrategy(entry.getErrorStrategy())
                .responsibleRoleId(entry.getResponsibleRoleId())
                .processCompleted(false)
                .build();
    }

    private String buildCacheKey(Long locationId, String processCode) {
        return "%s:%s:%s".formatted(CACHE_KEY_PREFIX, locationId, processCode);
    }
}
