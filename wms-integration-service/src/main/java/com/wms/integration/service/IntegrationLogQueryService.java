package com.wms.integration.service;

import com.wms.integration.api.dto.IntegrationLogFilter;
import com.wms.integration.api.dto.IntegrationLogResponse;
import com.wms.integration.entity.IntegrationLog;
import com.wms.integration.entity.IntegrationJob;
import com.wms.integration.entity.LocationIntegrationConfig;
import com.wms.integration.repository.IntegrationLogRepository;
import com.wms.integration.repository.IntegrationLogSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Entegrasyon log listeleme ve detay sorgulama servisi.
 *
 * <p>Tüm sorgular read-only transaction'da çalışır; flush yapılmaz,
 * Hibernate 2. seviye cache kullanılabilir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationLogQueryService {

    private final IntegrationLogRepository logRepository;

    /**
     * Filtre + sayfalama destekli log sorgusu.
     *
     * @param filter  status, locationId, jobCode filtreleri (null = filtre yok)
     * @param pageable sayfalama ve sıralama parametreleri
     * @return sayfalı {@link IntegrationLogResponse} listesi
     */
    @Transactional(readOnly = true)
    public Page<IntegrationLogResponse> queryLogs(IntegrationLogFilter filter, Pageable pageable) {
        IntegrationLogSpecification spec = new IntegrationLogSpecification(filter);
        return logRepository.findAll(spec, pageable)
                .map(log -> toResponse(log, filter.isIncludePayloads()));
    }

    /**
     * Tek log kaydının tüm detaylarını (payload dahil) döner.
     *
     * @param logId IntegrationLog Long
     * @return detaylı log yanıtı
     * @throws IntegrationLogNotFoundException kayıt bulunamazsa
     */
    @Transactional(readOnly = true)
    public IntegrationLogResponse getLogDetail(Long logId) {
        IntegrationLog log = logRepository.findById(logId)
                .orElseThrow(() -> new IntegrationLogNotFoundException(logId));
        return toResponse(log, true);  // detay endpoint'i her zaman payload döner
    }

    // -----------------------------------------------------------------------
    // Mapper
    // -----------------------------------------------------------------------

    private IntegrationLogResponse toResponse(IntegrationLog log, boolean includePayloads) {
        LocationIntegrationConfig config = log.getLocationIntegrationConfig();
        IntegrationJob job = log.getIntegrationJob();

        return IntegrationLogResponse.builder()
                .id(log.getId())
                .locationId(config != null ? config.getLocationId() : null)
                .locationName(config != null && config.getIntegrationSystem() != null
                        ? config.getIntegrationSystem().getName() : null)
                .erpSystemCode(config != null && config.getIntegrationSystem() != null
                        ? config.getIntegrationSystem().getCode() : null)
                .jobCode(job != null ? job.getCode() : null)
                .jobName(job != null ? job.getName() : null)
                .status(log.getStatus())
                .retryCount(log.getRetryCount())
                .externalReference(log.getExternalReference())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .lastAttemptAt(log.getLastAttemptAt())
                .outboxMessageId(log.getOutboxMessageId())
                .requestPayload(includePayloads ? log.getRequestPayload() : null)
                .responsePayload(includePayloads ? log.getResponsePayload() : null)
                .build();
    }

    // -----------------------------------------------------------------------
    // Inner exception
    // -----------------------------------------------------------------------

    public static class IntegrationLogNotFoundException extends RuntimeException {
        public IntegrationLogNotFoundException(Long logId) {
            super("IntegrationLog not found: id=" + logId);
        }
    }
}
