package com.wms.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Sözleşme oluşturulurken para birimi uyum kontrolü.
 *
 * <p>Müşteri para birimi izin politikası {@link CustomerCurrencyValidator} ile aynıdır.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractCurrencyValidator {

    private final CustomerCurrencyValidator customerCurrencyValidator;

    public void validateContractCurrency(Long customerId, Long currencyId) {
        customerCurrencyValidator.validateCurrencyPermission(customerId, currencyId);
    }
}
