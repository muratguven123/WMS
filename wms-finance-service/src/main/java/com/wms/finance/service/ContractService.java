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

import com.wms.finance.dto.ContractFixedRateDto;
import com.wms.finance.dto.ContractFixedRateRequest;
import com.wms.finance.entity.ContractFixedRate;
import com.wms.finance.entity.ExchangeRateAuditLog;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.repository.ContractFixedRateRepository;
import com.wms.finance.repository.ContractRepository;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.ExchangeRateAuditLogRepository;
import com.wms.finance.repository.FinanceCustomerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sözleşme yaşam döngüsü yönetimi.
 *
 * <p>Kayıt öncesinde {@link ContractCurrencyValidator} çalıştırılır;
 * izinsiz para birimiyle gelen sözleşmeler DB'ye yazılmaz.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ContractService {

    private final ContractCurrencyValidator contractCurrencyValidator;
    private final ContractRepository contractRepository;
    private final FinanceCustomerRepository customerRepository;
    private final CurrencyRepository currencyRepository;
    private final ContractFixedRateRepository contractFixedRateRepository;
    private final ExchangeRateAuditLogRepository auditLogRepository;
    private final ExchangeRateCacheService cacheService;

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

    // ── Contract Fixed Rates CRUD ─────────────────────────────────────────────

    public List<ContractFixedRateDto> getFixedRates(Long contractId) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        return contractFixedRateRepository.findAll().stream()
                .filter(rate -> rate.getContract().getId().equals(contractId))
                .map(this::toFixedRateDto)
                .toList();
    }

    public ContractFixedRateDto addFixedRate(Long contractId, ContractFixedRateRequest request, Long userId) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        // 1. Para birimi tutarlılık ve izin kontrolü
        contractCurrencyValidator.validateFixedRateCurrencies(
                contract, request.sourceCurrencyId(), request.targetCurrencyId());

        // 2. Tarih aralığı çakışmazlık kontrolü
        checkOverlap(contractId, request.sourceCurrencyId(), request.targetCurrencyId(),
                request.rateType(), request.validFrom(), request.validTo(), null);

        var source = currencyRepository.findById(request.sourceCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Source currency not found: " + request.sourceCurrencyId()));
        var target = currencyRepository.findById(request.targetCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Target currency not found: " + request.targetCurrencyId()));

        var fixedRate = ContractFixedRate.builder()
                .contract(contract)
                .sourceCurrency(source)
                .targetCurrency(target)
                .rate(request.rate())
                .rateType(request.rateType())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .active(request.active() != null ? request.active() : true)
                .build();

        var saved = contractFixedRateRepository.save(fixedRate);

        // 3. Değişiklikleri ExchangeRateAuditLog'a yaz
        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .contractFixedRate(saved)
                .actionType(AuditActionType.INSERT)
                .oldRate(null)
                .newRate(saved.getRate())
                .userId(userId)
                .build());

        // 4. Cache temizleme
        evictCache(source.getCode(), target.getCode(), saved.getValidFrom());

        return toFixedRateDto(saved);
    }

    public ContractFixedRateDto updateFixedRate(Long contractId, Long rateId, ContractFixedRateRequest request, Long userId) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        var fixedRate = contractFixedRateRepository.findById(rateId)
                .orElseThrow(() -> new IllegalArgumentException("Contract fixed rate not found: " + rateId));

        if (!fixedRate.getContract().getId().equals(contractId)) {
            throw new IllegalArgumentException("Contract fixed rate does not belong to the specified contract");
        }

        // 1. Para birimi tutarlılık ve izin kontrolü
        contractCurrencyValidator.validateFixedRateCurrencies(
                contract, request.sourceCurrencyId(), request.targetCurrencyId());

        // 2. Tarih aralığı çakışmazlık kontrolü
        checkOverlap(contractId, request.sourceCurrencyId(), request.targetCurrencyId(),
                request.rateType(), request.validFrom(), request.validTo(), rateId);

        var source = currencyRepository.findById(request.sourceCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Source currency not found: " + request.sourceCurrencyId()));
        var target = currencyRepository.findById(request.targetCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Target currency not found: " + request.targetCurrencyId()));

        BigDecimal oldRate = fixedRate.getRate();

        fixedRate.setSourceCurrency(source);
        fixedRate.setTargetCurrency(target);
        fixedRate.setRate(request.rate());
        fixedRate.setRateType(request.rateType());
        fixedRate.setValidFrom(request.validFrom());
        fixedRate.setValidTo(request.validTo());
        if (request.active() != null) {
            fixedRate.setActive(request.active());
        }

        var saved = contractFixedRateRepository.save(fixedRate);

        // 3. Değişiklikleri ExchangeRateAuditLog'a yaz
        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .contractFixedRate(saved)
                .actionType(AuditActionType.UPDATE)
                .oldRate(oldRate)
                .newRate(saved.getRate())
                .userId(userId)
                .build());

        // 4. Cache temizleme
        evictCache(source.getCode(), target.getCode(), saved.getValidFrom());

        return toFixedRateDto(saved);
    }

    public void deleteFixedRate(Long contractId, Long rateId, Long userId) {
        var contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));

        var fixedRate = contractFixedRateRepository.findById(rateId)
                .orElseThrow(() -> new IllegalArgumentException("Contract fixed rate not found: " + rateId));

        if (!fixedRate.getContract().getId().equals(contractId)) {
            throw new IllegalArgumentException("Contract fixed rate does not belong to the specified contract");
        }

        // 1. Değişiklikleri ExchangeRateAuditLog'a yaz
        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .contractFixedRate(fixedRate)
                .actionType(AuditActionType.DELETE)
                .oldRate(fixedRate.getRate())
                .newRate(fixedRate.getRate())
                .userId(userId)
                .build());

        contractFixedRateRepository.delete(fixedRate);

        // 2. Cache temizleme
        evictCache(fixedRate.getSourceCurrency().getCode(), fixedRate.getTargetCurrency().getCode(), fixedRate.getValidFrom());
    }

    private void checkOverlap(Long contractId, Long sourceCurrencyId, Long targetCurrencyId, RateType rateType,
                              LocalDate validFrom, LocalDate validTo, Long idToExclude) {
        var existingRates = contractFixedRateRepository.findOverlappingRates(
                contractId, sourceCurrencyId, targetCurrencyId, rateType, idToExclude);

        for (var ex : existingRates) {
            boolean overlaps = (ex.getValidTo() == null || !validFrom.isAfter(ex.getValidTo()))
                    && (validTo == null || !validTo.isBefore(ex.getValidFrom()));
            if (overlaps) {
                throw new IllegalArgumentException(String.format(
                        "Date range [%s - %s] overlaps with existing active fixed rate [%s - %s] for contract %d",
                        validFrom, validTo != null ? validTo : "∞",
                        ex.getValidFrom(), ex.getValidTo() != null ? ex.getValidTo() : "∞",
                        contractId));
            }
        }
    }

    private void evictCache(String sourceCode, String targetCode, LocalDate rateDate) {
        cacheService.evict(sourceCode, targetCode, rateDate);
    }

    private ContractFixedRateDto toFixedRateDto(ContractFixedRate entity) {
        return ContractFixedRateDto.builder()
                .id(entity.getId())
                .contractId(entity.getContract().getId())
                .sourceCurrencyId(entity.getSourceCurrency().getId())
                .sourceCurrencyCode(entity.getSourceCurrency().getCode())
                .targetCurrencyId(entity.getTargetCurrency().getId())
                .targetCurrencyCode(entity.getTargetCurrency().getCode())
                .rate(entity.getRate())
                .rateType(entity.getRateType())
                .validFrom(entity.getValidFrom())
                .validTo(entity.getValidTo())
                .active(entity.isActive())
                .build();
    }
}
