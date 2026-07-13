package com.wms.finance.service;

import com.wms.finance.dto.ContractDto;
import com.wms.finance.dto.CreateContractRequest;
import com.wms.finance.entity.Contract;
import com.wms.finance.repository.ContractRepository;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sözleşme yaşam döngüsü yönetimi.
 *
 * <p>Kayıt öncesinde {@link ContractCurrencyValidator} çalıştırılır;
 * izinsiz para birimiyle gelen sözleşmeler DB'ye yazılmaz.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ContractService {

    private final ContractCurrencyValidator contractCurrencyValidator;
    private final ContractRepository contractRepository;
    private final FinanceCustomerRepository customerRepository;
    private final CurrencyRepository currencyRepository;

    /**
     * Yeni sözleşme oluşturur.
     *
     * @throws com.wms.finance.exception.InvalidCustomerCurrencyException
     *         sözleşmenin para birimi müşteri için izinli değilse
     */
    public ContractDto createContract(CreateContractRequest request) {
        // 1. Para birimi izin kontrolü — izinsizse buradan exception fırlar
        contractCurrencyValidator.validateContractCurrency(
                request.customerId(), request.currencyId());

        // 2. Bağımlı entity'leri getir
        var customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: " + request.customerId()));

        var currency = currencyRepository.findById(request.currencyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Currency not found: " + request.currencyId()));

        // 3. Sözleşmeyi kaydet
        var contract = Contract.builder()
                .customer(customer)
                .currency(currency)
                .contractCode(request.contractCode())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        var saved = contractRepository.save(contract);

        return ContractDto.builder()
                .id(saved.getId())
                .customerId(customer.getId())
                .currencyId(currency.getId())
                .currencyCode(currency.getCode())
                .contractCode(saved.getContractCode())
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .build();
    }
}
