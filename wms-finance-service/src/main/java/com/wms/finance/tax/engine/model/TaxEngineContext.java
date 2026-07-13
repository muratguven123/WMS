package com.wms.finance.tax.engine.model;

import com.wms.finance.tax.audit.entity.TransactionType;

import java.time.LocalDate;
import java.util.List;

/**
 * Engine'e iletilen hesaplama bağlamı.
 *
 * @param countryId        opsiyonel — dolu ise {@link com.wms.finance.service.TaxResolutionService} ile oran çözülür
 * @param transactionDate  opsiyonel — temporal vergi oranı çözümlemesi için işlem tarihi
 */
public record TaxEngineContext(
        TransactionType transactionType,
        Long transactionReferenceId,
        List<TaxLineItem> items,
        List<TaxRule> rules,
        boolean inclusive,
        Long countryId,
        Long locationId,
        Long customerId,
        String productType,
        String operationType,
        LocalDate transactionDate
) {
    public TaxEngineContext {
        if (transactionType == null)
            throw new IllegalArgumentException("transactionType null olamaz.");
        if (transactionReferenceId == null)
            throw new IllegalArgumentException("transactionReferenceId null olamaz.");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("En az bir kalem gereklidir.");
        if (rules == null || rules.isEmpty())
            throw new IllegalArgumentException("En az bir vergi kuralı gereklidir.");
        items = List.copyOf(items);
        rules = List.copyOf(rules);
    }

    public static TaxEngineContext of(
            TransactionType transactionType,
            Long transactionReferenceId,
            List<TaxLineItem> items,
            List<TaxRule> rules,
            boolean inclusive) {
        return new TaxEngineContext(
                transactionType, transactionReferenceId, items, rules, inclusive,
                null, null, null, null, null, null);
    }

    public boolean hasRateResolutionContext() {
        return countryId != null && transactionDate != null;
    }
}
