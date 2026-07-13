package com.wms.integration.api;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.integration.api.dto.IntegrationLogFilter;
import com.wms.integration.api.dto.IntegrationLogResponse;
import com.wms.integration.api.dto.RetryResponse;
import com.wms.integration.repository.IntegrationLogRepository.StatusCountProjection;
import com.wms.integration.service.IntegrationLogQueryService;
import com.wms.integration.service.IntegrationLogQueryService.IntegrationLogNotFoundException;
import com.wms.integration.service.IntegrationRetryService;
import com.wms.integration.service.IntegrationRetryService.OutboxMessageNotFoundException;
import com.wms.integration.service.IntegrationRetryService.RetryNotAllowedException;
import com.wms.integration.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entegrasyon izleme ve manuel retry REST API.
 *
 * <pre>
 * GET  /api/integrations/logs                    — Filtrelenebilir, sayfalı log listesi
 * GET  /api/integrations/logs/{logId}            — Tek log detayı (payload dahil)
 * POST /api/integrations/logs/{logId}/retry      — Manuel yeniden tetikleme (Force Retry)
 * GET  /api/integrations/stats                   — Son 24 saat durum istatistikleri
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INTEGRATION_ADMIN', 'WMS_ADMIN')")
public class IntegrationMonitorController {

    private final IntegrationLogQueryService logQueryService;
    private final IntegrationRetryService    retryService;
    private final IntegrationLogRepository   logRepository;

    // -----------------------------------------------------------------------
    // GET /api/integrations/logs
    // -----------------------------------------------------------------------

    /**
     * Filtre + sayfalama destekli entegrasyon log listesi.
     *
     * <p>Örnek sorgular:
     * <pre>
     *   GET /api/integrations/logs?status=FAILED&page=0&size=20
     *   GET /api/integrations/logs?locationId=&lt;uuid&gt;&jobCode=STOCK_MOVE
     *   GET /api/integrations/logs?status=FAILED&includePayloads=true
     * </pre>
     *
     * @param filter   filtre parametreleri (@ModelAttribute ile query string'den bind edilir)
     * @param pageable sayfa, boyut, sıralama (default: page=0, size=20, sort=createdAt,desc)
     * @return sayfalı log listesi
     */
    @GetMapping("/logs")
    public ResponseEntity<Page<IntegrationLogResponse>> listLogs(
            @ModelAttribute IntegrationLogFilter filter,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.debug("[IntegrationMonitor] GET /logs filter={}, page={}", filter, pageable);
        Page<IntegrationLogResponse> page = logQueryService.queryLogs(filter, pageable);
        return ResponseEntity.ok(page);
    }

    // -----------------------------------------------------------------------
    // GET /api/integrations/logs/{logId}
    // -----------------------------------------------------------------------

    /**
     * Tek log kaydının tüm detaylarını döner (requestPayload, responsePayload dahil).
     *
     * @param logId IntegrationLog Long
     */
    @GetMapping("/logs/{logId}")
    public ResponseEntity<IntegrationLogResponse> getLogDetail(
            @PathVariable Long logId) {

        log.debug("[IntegrationMonitor] GET /logs/{}", logId);
        IntegrationLogResponse response = logQueryService.getLogDetail(logId);
        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // POST /api/integrations/logs/{logId}/retry
    // -----------------------------------------------------------------------

    /**
     * Başarısız entegrasyon kaydını manuel olarak yeniden kuyruğa alır.
     *
     * <p>Sadece {@code FAILED} veya {@code RETRYING} durumundaki loglar
     * yeniden tetiklenebilir. Outbox Worker 5 saniye içinde işler.
     *
     * @param logId IntegrationLog Long
     * @return retry zamanlama bilgisi
     */
    @PostMapping("/logs/{logId}/retry")
    public ResponseEntity<RetryResponse> forceRetry(
            @PathVariable Long logId) {

        log.info("[IntegrationMonitor] POST /logs/{}/retry", logId);
        RetryResponse response = retryService.forceRetry(logId);
        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // GET /api/integrations/stats
    // -----------------------------------------------------------------------

    /**
     * Son 24 saat entegrasyon durum istatistikleri.
     * Dashboard / alerting entegrasyonu için kullanılabilir.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        OffsetDateTime since = OffsetDateTime.now().minusHours(24);
        List<StatusCountProjection> counts = logRepository.countByStatusSince(since);

        Map<String, Long> statusMap = new java.util.LinkedHashMap<>();
        for (StatusCountProjection p : counts) {
            statusMap.put(p.getStatus().name(), p.getCount());
        }

        return ResponseEntity.ok(Map.of(
                "since",  since.toString(),
                "counts", statusMap
        ));
    }

    // -----------------------------------------------------------------------
    // Exception handlers (controller-local)
    // -----------------------------------------------------------------------

    @ExceptionHandler(IntegrationLogNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IntegrationLogNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RetryNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleRetryNotAllowed(RetryNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(OutboxMessageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOutboxNotFound(OutboxMessageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }
}
