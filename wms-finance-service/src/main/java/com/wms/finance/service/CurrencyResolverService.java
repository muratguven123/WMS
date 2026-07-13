package com.wms.finance.service;

import com.wms.finance.entity.CompanyCurrencySetting;
import com.wms.finance.entity.Contract;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.FinanceCustomer;
import com.wms.finance.entity.LocationCurrencySetting;
import com.wms.finance.entity.SystemConfig;
import com.wms.finance.exception.CurrencyNotConfiguredException;
import com.wms.finance.repository.CompanyCurrencySettingRepository;
import com.wms.finance.repository.ContractRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import com.wms.finance.repository.LocationCurrencySettingRepository;
import com.wms.finance.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Sözleşme → Müşteri → Depo → Şirket → Sistem varsayılanı hiyerarşisine göre
 * işlem para birimini çözer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencyResolverService {

    private final ContractRepository contractRepository;
    private final FinanceCustomerRepository customerRepository;
    private final LocationCurrencySettingRepository locationRepository;
    private final CompanyCurrencySettingRepository companyRepository;
    private final SystemConfigRepository systemConfigRepository;

    public Currency resolveTransactionCurrency(
            Long companyId,
            Long locationId,
            Long customerId,
            Long contractId
    ) {
        Currency fromContract = resolveFromContract(contractId);
        if (fromContract != null) {
            log.debug("Currency resolved from contract: {}", fromContract.getCode());
            return fromContract;
        }

        Currency fromCustomer = resolveFromCustomer(customerId);
        if (fromCustomer != null) {
            log.debug("Currency resolved from customer: {}", fromCustomer.getCode());
            return fromCustomer;
        }

        Currency fromLocation = resolveFromLocation(locationId);
        if (fromLocation != null) {
            log.debug("Currency resolved from location: {}", fromLocation.getCode());
            return fromLocation;
        }

        Currency fromCompany = resolveFromCompany(companyId);
        if (fromCompany != null) {
            log.debug("Currency resolved from company: {}", fromCompany.getCode());
            return fromCompany;
        }

        Currency systemDefault = systemConfigRepository.findFirstWithDefaultCurrency()
                .map(SystemConfig::getDefaultCurrency)
                .filter(Currency::isActive)
                .orElse(null);

        if (systemDefault != null) {
            log.debug("Currency resolved from system default: {}", systemDefault.getCode());
            return systemDefault;
        }

        throw new CurrencyNotConfiguredException();
    }

    private Currency resolveFromContract(Long contractId) {
        if (contractId == null) {
            return null;
        }
        return contractRepository.findByIdWithCurrency(contractId)
                .map(Contract::getCurrency)
                .filter(Currency::isActive)
                .orElse(null);
    }

    private Currency resolveFromCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findByIdWithCurrency(customerId)
                .map(FinanceCustomer::getDefaultCurrency)
                .filter(c -> c != null && c.isActive())
                .orElse(null);
    }

    private Currency resolveFromLocation(Long locationId) {
        if (locationId == null) {
            return null;
        }
        return locationRepository.findByLocationIdWithCurrency(locationId)
                .map(LocationCurrencySetting::getLocalCurrency)
                .filter(Currency::isActive)
                .orElse(null);
    }

    private Currency resolveFromCompany(Long companyId) {
        if (companyId == null) {
            return null;
        }
        return companyRepository.findByCompanyIdWithCurrency(companyId)
                .map(CompanyCurrencySetting::getBaseCurrency)
                .filter(Currency::isActive)
                .orElse(null);
    }
}
