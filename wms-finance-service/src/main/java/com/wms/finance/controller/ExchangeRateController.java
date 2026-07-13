package com.wms.finance.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.wms.finance.dto.ActiveRateListDto;
import com.wms.finance.dto.ManualRateRequest;
import com.wms.finance.dto.ManualRateResponse;
import com.wms.finance.dto.TcmbSyncResponse;
import com.wms.finance.job.TcmbRateSyncJob;
import com.wms.finance.service.ActiveRateQueryService;
import com.wms.finance.service.ManualRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Döviz kuru yönetim API'leri.
 *
 * <p>Yetkilendirme: {@code X-User-Id} header'ı üzerinden kullanıcı kimliği alınır.
 * Gerçek auth entegrasyonu eklenene kadar bu header zorunlu değildir;
 * eksik olduğunda audit log'a {@code null} yazılır.
 */
@Slf4j
@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_USER', 'FINANCE_MANAGER', 'WMS_ADMIN')")
public class ExchangeRateController {

    private final ManualRateService manualRateService;
    private final ActiveRateQueryService activeRateQueryService;
    private final TcmbRateSyncJob tcmbRateSyncJob;

    /**
     * Aktif (güncel) kurları base para birimine karşı listeler.
     * Her satır mevcut {@code lookupRate} motoru üzerinden çözülür.
     */
    @GetMapping("/active")
    public ActiveRateListDto listActiveRates(
            @RequestParam(defaultValue = "TRY") String baseCurrency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate rateDate,
            @RequestParam(defaultValue = "SELLING") String rateType) {

        return activeRateQueryService.listActiveRates(baseCurrency, rateDate, rateType);
    }

    /**
     * TCMB'den güncel kurları manuel olarak çeker ve upsert eder.
     */
    @PostMapping("/sync/tcmb")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'WMS_ADMIN')")
    public ResponseEntity<TcmbSyncResponse> syncTcmbRates() {
        log.info("[API] TCMB kur senkronizasyonu tetiklendi.");
        TcmbSyncResponse response = tcmbRateSyncJob.syncTcmbRatesWithResult();
        HttpStatus status = response.success() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Manuel döviz kuru ekler veya günceller.
     *
     * <p>Aynı (sourceCurrency, targetCurrency, rateDate, rateType, rateSource=MANUAL)
     * kombinasyonu için kayıt varsa UPDATE, yoksa INSERT işlemi yapılır.
     * Her iki durumda da audit log ve Redis cache eviction tetiklenir.
     *
     * @param userId  işlemi yapan kullanıcı Long'si (opsiyonel header)
     * @param request kur bilgileri
     * @return 201 Created (yeni kayıt) veya 200 OK (güncelleme)
     */
    @PostMapping("/manual")
    public ResponseEntity<ManualRateResponse> upsertManualRate(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ManualRateRequest request) {

        log.info("[API] Manuel kur isteği → source={} target={} date={} type={} userId={}",
                request.sourceCurrency(), request.targetCurrency(),
                request.rateDate(), request.rateType(), userId);

        ManualRateResponse response = manualRateService.upsert(request, userId);

        HttpStatus status = switch (response.action()) {
            case INSERT -> HttpStatus.CREATED;
            default     -> HttpStatus.OK;
        };

        return ResponseEntity.status(status).body(response);
    }
}
