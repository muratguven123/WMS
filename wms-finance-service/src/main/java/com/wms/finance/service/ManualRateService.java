package com.wms.finance.service;

import com.wms.finance.dto.ManualRateRequest;
import com.wms.finance.dto.ManualRateResponse;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.ExchangeRateAuditLog;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateAuditLogRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Manuel döviz kuru ekleme ve güncelleme işlemlerini yöneten servis.
 *
 * <p>Her işlemde:
 * <ol>
 *   <li>Upsert (INSERT veya UPDATE) ile {@code exchange_rates} tablosu güncellenir.</li>
 *   <li>{@code exchange_rate_audit_logs} tablosuna ilgili log kaydı yazılır.</li>
 *   <li>Etkilenen tüm Redis anahtarları silinerek cache tutarlılığı sağlanır.</li>
 * </ol>
 *
 * <p>Cache key formatı (CurrencyConversionService ile uyumlu):
 * {@code rate:<source>:<target>:<date>:<rateType>}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualRateService {

    private final ExchangeRateRepository        exchangeRateRepository;
    private final ExchangeRateAuditLogRepository auditLogRepository;
    private final CurrencyRepository             currencyRepository;
    private final ExchangeRateCacheService       cacheService;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Manuel kur ekler veya günceller.
     *
     * @param request işlem isteği
     * @param userId  işlemi yapan kullanıcı Long'si (nullable — yetkilendirme katmanından gelir)
     * @return işlem sonucu ve audit özeti
     */
    @Transactional
    public ManualRateResponse upsert(ManualRateRequest request, Long userId) {
        String sourceCode = request.sourceCurrency().toUpperCase();
        String targetCode = request.targetCurrency().toUpperCase();

        Currency source = loadCurrency(sourceCode);
        Currency target = loadCurrency(targetCode);

        Optional<ExchangeRate> existing = exchangeRateRepository.findExactRate(
                sourceCode,
                targetCode,
                request.rateDate(),
                request.rateType(),
                RateSource.MANUAL);

        ManualRateResponse response;

        if (existing.isPresent()) {
            response = performUpdate(existing.get(), request.rate(), userId);
        } else {
            response = performInsert(source, target, request, userId);
        }

        evictCache(sourceCode, targetCode, request.rateDate());

        return response;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────────

    private ManualRateResponse performInsert(Currency source, Currency target,
                                             ManualRateRequest request, Long userId) {
        ExchangeRate newRate = ExchangeRate.builder()
                .sourceCurrency(source)
                .targetCurrency(target)
                .rateDate(request.rateDate())
                .rateType(request.rateType())
                .rateSource(RateSource.MANUAL)
                .rate(request.rate())
                .build();

        ExchangeRate saved = exchangeRateRepository.save(newRate);

        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .exchangeRate(saved)
                .actionType(AuditActionType.INSERT)
                .oldRate(null)
                .newRate(request.rate())
                .userId(userId)
                .build());

        log.info("[MANUAL-RATE] INSERT → {} → {} | {} | {} | rate={}",
                source.getCode(), target.getCode(),
                request.rateDate(), request.rateType(), request.rate());

        return toResponse(saved, AuditActionType.INSERT, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────────

    private ManualRateResponse performUpdate(ExchangeRate record, BigDecimal newRateValue,
                                             Long userId) {
        BigDecimal oldRate = record.getRate();

        if (oldRate.compareTo(newRateValue) == 0) {
            log.debug("[MANUAL-RATE] Kur değişmedi, güncelleme atlanıyor: {} {}",
                    record.getSourceCurrency().getCode(), record.getRateDate());
            return toResponse(record, AuditActionType.UPDATE, oldRate);
        }

        record.setRate(newRateValue);
        exchangeRateRepository.save(record);

        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .exchangeRate(record)
                .actionType(AuditActionType.UPDATE)
                .oldRate(oldRate)
                .newRate(newRateValue)
                .userId(userId)
                .build());

        log.info("[MANUAL-RATE] UPDATE → {} → {} | {} | {} | {} → {}",
                record.getSourceCurrency().getCode(),
                record.getTargetCurrency().getCode(),
                record.getRateDate(), record.getRateType(),
                oldRate, newRateValue);

        return toResponse(record, AuditActionType.UPDATE, oldRate);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cache eviction
    // ──────────────────────────────────────────────────────────────────────────

    private void evictCache(String sourceCode, String targetCode, java.time.LocalDate rateDate) {
        cacheService.evict(sourceCode, targetCode, rateDate);
    }

    private ManualRateResponse toResponse(ExchangeRate rate, AuditActionType action,
                                          BigDecimal previousRate) {
        return new ManualRateResponse(
                rate.getId(),
                rate.getSourceCurrency().getCode(),
                rate.getTargetCurrency().getCode(),
                rate.getRateDate(),
                rate.getRateType(),
                rate.getRate(),
                action,
                previousRate
        );
    }

    private Currency loadCurrency(String code) {
        return currencyRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tanımlı veya aktif para birimi bulunamadı: " + code));
    }
}
