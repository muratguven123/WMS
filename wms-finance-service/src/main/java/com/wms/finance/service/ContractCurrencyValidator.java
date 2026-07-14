package com.wms.finance.service;

import com.wms.finance.entity.Contract;
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

    public void validateFixedRateCurrencies(Contract contract, Long sourceCurrencyId, Long targetCurrencyId) {
        Long contractCurrencyId = contract.getCurrency().getId();
        if (!sourceCurrencyId.equals(contractCurrencyId) && !targetCurrencyId.equals(contractCurrencyId)) {
            throw new IllegalArgumentException(
                    "Fixed rate currencies must be consistent with the contract currency: " + contract.getCurrency().getCode());
        }
        customerCurrencyValidator.validateCurrencyPermission(contract.getCustomer().getId(), sourceCurrencyId);
        customerCurrencyValidator.validateCurrencyPermission(contract.getCustomer().getId(), targetCurrencyId);
    }
}
