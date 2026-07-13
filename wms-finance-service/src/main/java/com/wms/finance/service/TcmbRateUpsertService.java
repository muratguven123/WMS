package com.wms.finance.service;

import com.wms.finance.dto.TcmbUpsertResult;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.ExchangeRate;
import com.wms.finance.entity.ExchangeRateAuditLog;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.integration.tcmb.TcmbCurrencyDefaults;
import com.wms.finance.integration.tcmb.TcmbParsedRate;
import com.wms.finance.integration.tcmb.TcmbSyncNotificationService;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateAuditLogRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TcmbRateUpsertService {

    private static final String TRY_CODE = "TRY";

    private final TcmbSyncNotificationService notificationService;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateAuditLogRepository auditLogRepository;
    private final ExchangeRateCacheService cacheService;

    private final Map<String, Currency> currencyCache = new ConcurrentHashMap<>();

    @Transactional
    public TcmbUpsertResult upsertRates(List<TcmbParsedRate> parsedRates) {
        if (parsedRates == null || parsedRates.isEmpty()) {
            return TcmbUpsertResult.empty();
        }

        Currency tryCurrency = resolveCurrency(TRY_CODE);
        if (tryCurrency == null) {
            log.error("[TCMB-SYNC] TRY para birimi DB'de bulunamadı. Upsert iptal edildi.");
            notificationService.notifyAdminOnSyncFailure(
                    "MISSING_CURRENCY", "TRY para birimi tanımlı değil.");
            return TcmbUpsertResult.empty();
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        Set<LocalDate> affectedDates = new HashSet<>();
        LocalDate primaryRateDate = null;

        for (TcmbParsedRate parsed : parsedRates) {
            Currency foreign = resolveOrCreateCurrency(parsed.currencyCode());
            if (foreign == null) {
                log.warn("[TCMB-SYNC] {} para birimi desteklenmiyor veya oluşturulamadı, atlanıyor.",
                        parsed.currencyCode());
                skipped++;
                continue;
            }

            affectedDates.add(parsed.rateDate());
            if (primaryRateDate == null) {
                primaryRateDate = parsed.rateDate();
            }

            Optional<ExchangeRate> existing = exchangeRateRepository.findExactRate(
                    foreign.getCode(),
                    TRY_CODE,
                    parsed.rateDate(),
                    parsed.rateType(),
                    RateSource.TCMB);

            if (existing.isEmpty()) {
                insert(foreign, tryCurrency, parsed);
                inserted++;
            } else {
                ExchangeRate record = existing.get();
                if (rateChanged(record.getRate(), parsed.rate())) {
                    update(record, parsed.rate());
                    updated++;
                } else {
                    skipped++;
                }
            }
        }

        affectedDates.forEach(cacheService::evictAllForDate);

        log.info("[TCMB-SYNC] Upsert özeti → INSERT: {}, UPDATE: {}, SKIP: {}",
                inserted, updated, skipped);

        return new TcmbUpsertResult(inserted, updated, skipped, primaryRateDate);
    }

    private void insert(Currency source, Currency target, TcmbParsedRate parsed) {
        ExchangeRate saved = exchangeRateRepository.save(ExchangeRate.builder()
                .sourceCurrency(source)
                .targetCurrency(target)
                .rateDate(parsed.rateDate())
                .rateType(parsed.rateType())
                .rateSource(RateSource.TCMB)
                .rate(parsed.rate())
                .build());

        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .exchangeRate(saved)
                .actionType(AuditActionType.INSERT)
                .oldRate(null)
                .newRate(parsed.rate())
                .build());
    }

    private void update(ExchangeRate record, BigDecimal newRateValue) {
        BigDecimal oldRate = record.getRate();
        record.setRate(newRateValue);
        exchangeRateRepository.save(record);

        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .exchangeRate(record)
                .actionType(AuditActionType.UPDATE)
                .oldRate(oldRate)
                .newRate(newRateValue)
                .build());
    }

    private Currency resolveOrCreateCurrency(String code) {
        Currency existing = resolveCurrency(code);
        if (existing != null) {
            return existing;
        }
        if (!TcmbCurrencyDefaults.isSupported(code)) {
            return null;
        }

        Currency created = currencyRepository.save(Currency.builder()
                .code(code)
                .symbol(TcmbCurrencyDefaults.symbol(code))
                .decimalPlaces(TcmbCurrencyDefaults.decimalPlaces(code))
                .name(TcmbCurrencyDefaults.name(code))
                .active(true)
                .build());

        currencyCache.put(code, created);
        log.info("[TCMB-SYNC] {} para birimi otomatik oluşturuldu.", code);
        return created;
    }

    private Currency resolveCurrency(String code) {
        return currencyCache.computeIfAbsent(code, c ->
                currencyRepository.findByCodeAndActiveTrue(c).orElse(null));
    }

    private boolean rateChanged(BigDecimal existing, BigDecimal incoming) {
        return existing.compareTo(incoming) != 0;
    }
}
