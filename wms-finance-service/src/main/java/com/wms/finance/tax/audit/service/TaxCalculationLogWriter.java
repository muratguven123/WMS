package com.wms.finance.tax.audit.service;

import com.wms.finance.tax.audit.entity.TaxCalculationLog;
import com.wms.finance.tax.audit.entity.TaxType;
import com.wms.finance.tax.audit.entity.TransactionType;
import com.wms.finance.tax.audit.repository.TaxCalculationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vergi hesaplama audit kayıtlarını ayrı transaction'da yazar.
 * Self-invocation sorununu önlemek için {@link com.wms.finance.tax.engine.service.TaxEngineService}
 * dışında tutulur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxCalculationLogWriter {

    private final TaxCalculationLogRepository logRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaxCalculationLog persist(
            TransactionType transactionType,
            Long transactionReferenceId,
            TaxType taxType,
            BigDecimal taxRate,
            BigDecimal baseAmount,
            BigDecimal taxAmount,
            boolean inclusive,
            boolean exempt,
            String exemptionCode,
            String calculationSource,
            LocalDateTime calculationDate) {

        TaxCalculationLog logEntry = TaxCalculationLog.builder()
                .transactionType(transactionType)
                .transactionReferenceId(transactionReferenceId)
                .taxType(taxType)
                .taxRate(taxRate)
                .taxBaseAmount(baseAmount)
                .calculatedTaxAmount(taxAmount)
                .inclusive(inclusive)
                .exempt(exempt)
                .exemptionCode(exemptionCode)
                .calculationSource(calculationSource)
                .calculationDate(calculationDate)
                .build();

        TaxCalculationLog saved = logRepository.save(logEntry);
        log.debug("[TaxEngine] Log kaydedildi. logId={} transactionRef={}",
                saved.getId(), transactionReferenceId);
        return saved;
    }
}
