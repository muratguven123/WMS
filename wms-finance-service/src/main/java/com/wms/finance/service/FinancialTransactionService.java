package com.wms.finance.service;

import com.wms.finance.dto.ConversionResultDto;
import com.wms.finance.dto.CreateFinancialTransactionRequest;
import com.wms.finance.dto.FinancialTransactionDto;
import com.wms.finance.entity.Contract;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.FinancialTransaction;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.repository.CompanyCurrencySettingRepository;
import com.wms.finance.repository.ContractRepository;
import com.wms.finance.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FinancialTransactionService {

    private final CurrencyResolverService currencyResolverService;
    private final CurrencyConversionService currencyConversionService;
    private final CompanyCurrencySettingRepository companyCurrencySettingRepository;
    private final ContractRepository contractRepository;
    private final FinancialTransactionRepository transactionRepository;

    /**
     * Hiyerarşik para birimi çözümlemesi + kur çevrimi ile finansal işlem kaydı oluşturur.
     */
    public FinancialTransactionDto recordTransaction(CreateFinancialTransactionRequest request) {
        Currency transactionCurrency = currencyResolverService.resolveTransactionCurrency(
                request.companyId(),
                request.locationId(),
                request.customerId(),
                request.contractId()
        );

        Currency baseCurrency = companyCurrencySettingRepository
                .findByCompanyIdWithCurrency(request.companyId())
                .map(setting -> setting.getBaseCurrency())
                .orElse(transactionCurrency);

        String rateType = request.rateType() != null ? request.rateType() : RateType.SELLING.name();

        ConversionResultDto conversion = currencyConversionService.convert(
                request.amount(),
                transactionCurrency.getCode(),
                baseCurrency.getCode(),
                request.transactionDate(),
                rateType
        );

        Contract contract = request.contractId() != null
                ? contractRepository.findById(request.contractId()).orElse(null)
                : null;

        FinancialTransaction entity = FinancialTransaction.builder()
                .companyId(request.companyId())
                .locationId(request.locationId())
                .contract(contract)
                .originalCurrency(transactionCurrency)
                .originalAmount(conversion.originalAmount())
                .exchangeRate(conversion.exchangeRate())
                .baseCurrency(baseCurrency)
                .convertedAmount(conversion.convertedAmount())
                .transactionDate(request.transactionDate())
                .fallbackRateUsed(conversion.fallbackRateUsed())
                .build();

        FinancialTransaction saved = transactionRepository.save(entity);
        return toDto(saved);
    }

    private FinancialTransactionDto toDto(FinancialTransaction entity) {
        return new FinancialTransactionDto(
                entity.getId(),
                entity.getCompanyId(),
                entity.getLocationId(),
                entity.getContract() != null ? entity.getContract().getId() : null,
                entity.getOriginalCurrency().getCode(),
                entity.getOriginalAmount(),
                entity.getExchangeRate(),
                entity.getBaseCurrency().getCode(),
                entity.getConvertedAmount(),
                entity.getTransactionDate(),
                entity.isFallbackRateUsed()
        );
    }
}
