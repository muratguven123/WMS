package com.wms.finance.tax.engine.service;

import com.wms.finance.exception.TaxResolutionException;
import com.wms.finance.service.TaxResolutionContext;
import com.wms.finance.service.TaxResolutionService;
import com.wms.finance.tax.audit.entity.TaxCalculationLog;
import com.wms.finance.tax.audit.entity.TaxType;
import com.wms.finance.tax.audit.entity.TransactionType;
import com.wms.finance.tax.audit.repository.AuditTaxTypeRepository;
import com.wms.finance.tax.audit.service.TaxCalculationLogWriter;
import com.wms.finance.tax.engine.factory.TaxStrategyFactory;
import com.wms.finance.tax.engine.model.*;
import com.wms.finance.tax.strategy.TaxCalculationResult;
import com.wms.finance.tax.strategy.TaxCalculationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxEngineService {

    private static final String ENGINE_VERSION = "TAX_ENGINE_V1";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final TaxStrategyFactory strategyFactory;
    private final AuditTaxTypeRepository taxTypeRepository;
    private final TaxCalculationLogWriter logWriter;
    private final TaxResolutionService taxResolutionService;

    @Transactional
    public List<TaxCalculationResultDto> calculate(TaxEngineContext context) {
        log.info("[TaxEngine] Hesaplama başladı. transactionType={} referenceId={}",
                context.transactionType(), context.transactionReferenceId());

        List<TaxCalculationResultDto> results = new ArrayList<>();

        for (TaxLineItem item : context.items()) {
            for (TaxRule rule : context.rules()) {
                results.add(processLineRule(item, rule, context));
            }
        }

        log.info("[TaxEngine] Hesaplama tamamlandı. {} sonuç üretildi.", results.size());
        return results;
    }

    @Transactional
    public TaxCalculationResultDto calculateExchangeDifferenceTax(
            BigDecimal exchangeDifferenceAmount,
            String taxTypeCode,
            LocalDate date,
            Long locationId,
            Long countryId) {

        if (exchangeDifferenceAmount == null)
            throw new IllegalArgumentException("exchangeDifferenceAmount null olamaz.");
        if (taxTypeCode == null || taxTypeCode.isBlank())
            throw new IllegalArgumentException("taxTypeCode boş olamaz.");
        if (date == null)
            throw new IllegalArgumentException("date null olamaz.");
        if (locationId == null)
            throw new IllegalArgumentException("locationId null olamaz.");

        log.info("[TaxEngine] Kur farkı vergisi hesaplanıyor. amount={} taxType={} date={}",
                exchangeDifferenceAmount, taxTypeCode, date);

        if (exchangeDifferenceAmount.compareTo(ZERO) <= 0) {
            log.info("[TaxEngine] Negatif/sıfır kur farkı — vergi hesaplanmadı.");
            return buildZeroResult(locationId, taxTypeCode, exchangeDifferenceAmount, TaxStrategyType.EXCLUSIVE);
        }

        TaxType taxType = resolveTaxType(taxTypeCode);
        BigDecimal rate = resolveRate(taxTypeCode, taxType, countryId, locationId, null, null, null, date);
        TaxCalculationStrategy strategy = strategyFactory.resolve(TaxStrategyType.EXCLUSIVE);
        TaxCalculationResult calcResult = strategy.calculateTax(exchangeDifferenceAmount, rate);

        TaxCalculationLog savedLog = logWriter.persist(
                TransactionType.TRANSACTION_FEE,
                locationId,
                taxType,
                rate,
                calcResult.baseAmount(),
                calcResult.taxAmount(),
                false,
                false,
                null,
                ENGINE_VERSION + "_FOREX",
                date.atStartOfDay());

        return new TaxCalculationResultDto(
                locationId,
                taxTypeCode,
                calcResult.baseAmount(),
                calcResult.taxAmount(),
                calcResult.grandTotal(),
                false,
                false,
                null,
                TaxStrategyType.EXCLUSIVE,
                savedLog.getId());
    }

    /** Geriye uyumluluk: countryId olmadan varsayılan oran kullanılır. */
    @Transactional
    public TaxCalculationResultDto calculateExchangeDifferenceTax(
            BigDecimal exchangeDifferenceAmount,
            String taxTypeCode,
            LocalDate date,
            Long locationId) {
        return calculateExchangeDifferenceTax(
                exchangeDifferenceAmount, taxTypeCode, date, locationId, null);
    }

    private TaxCalculationResultDto processLineRule(TaxLineItem item,
                                                     TaxRule rule,
                                                     TaxEngineContext context) {
        TaxType taxType = resolveTaxType(rule.taxTypeCode());

        BigDecimal taxAmount;
        BigDecimal baseAmount;
        BigDecimal grandTotal;
        BigDecimal appliedRate;

        if (rule.exempt()) {
            baseAmount = item.amount();
            taxAmount = ZERO;
            grandTotal = item.amount();
            appliedRate = taxType.getDefaultRate();
            log.debug("[TaxEngine] Muafiyet uygulandı. lineId={} exemptionCode={}",
                    item.lineId(), rule.exemptionCode());
        } else {
            TaxCalculationStrategy strategy = strategyFactory.resolve(rule.strategyType());
            appliedRate = resolveRate(
                    rule.effectiveRateLookupCode(),
                    taxType,
                    context.countryId(),
                    context.locationId(),
                    context.customerId(),
                    context.productType(),
                    context.operationType(),
                    context.transactionDate());

            TaxCalculationResult result = strategy.calculateTax(item.amount(), appliedRate);
            baseAmount = result.baseAmount();
            taxAmount = result.taxAmount();
            grandTotal = result.grandTotal();
        }

        TaxCalculationLog savedLog = logWriter.persist(
                context.transactionType(),
                context.transactionReferenceId(),
                taxType,
                appliedRate,
                baseAmount,
                taxAmount,
                context.inclusive(),
                rule.exempt(),
                rule.exemptionCode(),
                rule.calculationSource(),
                context.transactionDate() != null
                        ? context.transactionDate().atStartOfDay()
                        : LocalDateTime.now());

        return new TaxCalculationResultDto(
                item.lineId(),
                rule.taxTypeCode(),
                baseAmount,
                taxAmount,
                grandTotal,
                context.inclusive(),
                rule.exempt(),
                rule.exemptionCode(),
                rule.strategyType(),
                savedLog.getId());
    }

    private BigDecimal resolveRate(String lookupCode,
                                   TaxType taxType,
                                   Long countryId,
                                   Long locationId,
                                   Long customerId,
                                   String productType,
                                   String operationType,
                                   LocalDate transactionDate) {
        if (countryId != null && transactionDate != null) {
            try {
                return taxResolutionService.resolveTaxRate(
                        lookupCode,
                        transactionDate,
                        new TaxResolutionContext(
                                countryId, locationId, customerId, productType, operationType));
            } catch (TaxResolutionException ex) {
                log.warn("[TaxEngine] Oran çözümlenemedi ({}), varsayılan oran kullanılıyor: {}",
                        lookupCode, ex.getMessage());
            }
        }
        return taxType.getDefaultRate();
    }

    private TaxType resolveTaxType(String taxTypeCode) {
        return taxTypeRepository.findByCodeAndActiveTrue(taxTypeCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aktif vergi tipi bulunamadı: " + taxTypeCode));
    }

    private TaxCalculationResultDto buildZeroResult(Long lineId, String taxTypeCode,
                                                     BigDecimal amount,
                                                     TaxStrategyType strategyType) {
        BigDecimal safeAmount = amount == null ? ZERO : amount.abs();
        return new TaxCalculationResultDto(
                lineId, taxTypeCode,
                safeAmount, ZERO, safeAmount,
                false, false, null,
                strategyType, null);
    }
}
