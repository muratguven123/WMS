package com.wms.finance.service;

import com.wms.finance.dto.CustomerExchangeRateDto;
import com.wms.finance.dto.CustomerExchangeRateRequest;
import com.wms.finance.dto.ExchangeRateDto;
import com.wms.finance.entity.Contract;
import com.wms.finance.entity.CustomerExchangeRate;
import com.wms.finance.entity.ExchangeRateAuditLog;
import com.wms.finance.entity.enums.AuditActionType;
import com.wms.finance.entity.enums.ExchangeDiffPreference;
import com.wms.finance.entity.enums.RateSource;
import com.wms.finance.entity.enums.RateType;
import com.wms.finance.exception.ExchangeRateNotFoundException;
import com.wms.finance.repository.ContractFixedRateRepository;
import com.wms.finance.repository.ContractRepository;
import com.wms.finance.repository.CustomerExchangeRateRepository;
import com.wms.finance.repository.ExchangeRateAuditLogRepository;
import com.wms.finance.repository.ExchangeRateRepository;
import com.wms.finance.repository.FinanceCustomerRepository;
import com.wms.finance.repository.SystemConfigRepository;
import com.wms.finance.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Müşteri kartındaki {@code rateType} / {@code rateSource} parametrelerine göre
 * dinamik kur çekimi ve kur farkı hesaplama tercihi yönetimi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerRateService {

    private static final int MAX_FALLBACK_DAYS = 5;

    private final FinanceCustomerRepository customerRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final CustomerExchangeRateRepository customerExchangeRateRepository;
    private final ContractFixedRateRepository contractFixedRateRepository;
    private final ContractRepository contractRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateAuditLogRepository auditLogRepository;
    private final ExchangeRateCacheService cacheService;
    private final CustomerCurrencyValidator customerCurrencyValidator;

    // ── Kur Sorgulama ────────────────────────────────────────────────────────

    /**
     * Müşterinin kartında tanımlı {@code rateType} ve {@code rateSource} değerlerini
     * kullanarak iki para birimi arasındaki kuru döner.
     *
     * @param customerId     müşteri kimliği
     * @param sourceCurrency kaynak para birimi kodu (örn. "USD")
     * @param targetCurrency hedef para birimi kodu (örn. "TRY")
     * @param date           işlem tarihi
     * @return müşteriye özgü kur bilgisi
     * @throws IllegalArgumentException      müşteri bulunamazsa
     * @throws ExchangeRateNotFoundException uygun kur kayıt bulunamazsa
     */
    public ExchangeRateDto getCustomerRate(
            Long customerId,
            String sourceCurrency,
            String targetCurrency,
            Instant date) {
        return getCustomerRate(customerId, sourceCurrency, targetCurrency, date, null);
    }

    /**
     * Müşterinin kartında tanımlı {@code rateType} ve {@code rateSource} değerlerini
     * ve opsiyonel {@code contractId}'yi kullanarak iki para birimi arasındaki kuru döner.
     */
    public ExchangeRateDto getCustomerRate(
            Long customerId,
            String sourceCurrency,
            String targetCurrency,
            Instant date,
            Long contractId) {

        var customer = customerRepository.findByIdWithCurrency(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: " + customerId));

        var rateType   = customer.getRateType();
        var rateSource = customer.getRateSource();

        String src = sourceCurrency.trim().toUpperCase();
        String tgt = targetCurrency.trim().toUpperCase();
        LocalDate rateDate = date.atZone(ZoneOffset.UTC).toLocalDate();

        if (rateSource == RateSource.CONTRACT) {
            Contract contract = null;
            if (contractId != null) {
                contract = contractRepository.findById(contractId).orElse(null);
            }
            if (contract == null) {
                var dateTime = date.atZone(ZoneOffset.UTC).toLocalDateTime();
                var activeContracts = contractRepository.findActiveContracts(customerId, dateTime);
                if (!activeContracts.isEmpty()) {
                    contract = activeContracts.get(0);
                }
            }

            if (contract != null) {
                var fixedRates = contractFixedRateRepository.findActiveRates(contract.getId(), src, tgt, rateType);
                if (fixedRates.isEmpty()) {
                    var inverseFixedRates = contractFixedRateRepository.findActiveRates(contract.getId(), tgt, src, rateType);
                    if (!inverseFixedRates.isEmpty()) {
                        var ex = inverseFixedRates.get(0);
                        if (!rateDate.isBefore(ex.getValidFrom()) && (ex.getValidTo() == null || !rateDate.isAfter(ex.getValidTo()))) {
                            BigDecimal inverted = BigDecimal.ONE.divide(ex.getRate(), 12, java.math.RoundingMode.HALF_EVEN)
                                    .setScale(6, java.math.RoundingMode.HALF_EVEN);
                            return new ExchangeRateDto(src, tgt, inverted, rateDate, rateType.name(), RateSource.CONTRACT.name(), false);
                        }
                    }
                } else {
                    var ex = fixedRates.get(0);
                    if (!rateDate.isBefore(ex.getValidFrom()) && (ex.getValidTo() == null || !rateDate.isAfter(ex.getValidTo()))) {
                        return new ExchangeRateDto(src, tgt, ex.getRate(), rateDate, rateType.name(), RateSource.CONTRACT.name(), false);
                    }
                }
            }

            LocalDate minDate = rateDate.minusDays(MAX_FALLBACK_DAYS);
            for (RateSource source : List.of(RateSource.MANUAL, RateSource.TCMB)) {
                var fallback = exchangeRateRepository.findNearestPastRate(src, tgt, rateType.name(), source.name(), rateDate, minDate);
                if (fallback.isPresent()) {
                    log.warn("Müşteri [{}] için CONTRACT fallback kur kullanıldı: {}/{} type={} requested={} used={}",
                            customerId, src, tgt, rateType, rateDate, fallback.get().getRateDate());
                    return new ExchangeRateDto(src, tgt, fallback.get().getRate(), fallback.get().getRateDate(),
                            rateType.name(), source.name(), true);
                }
            }
            throw new ExchangeRateNotFoundException(src, tgt, rateType.name());
        }

        if (rateSource == RateSource.CUSTOMER) {
            var customerRateOpt = customerExchangeRateRepository.findExactRate(customerId, src, tgt, rateDate, rateType);
            if (customerRateOpt.isPresent()) {
                var cr = customerRateOpt.get();
                return new ExchangeRateDto(src, tgt, cr.getRate(), rateDate, rateType.name(), RateSource.CUSTOMER.name(), false);
            }

            var config = systemConfigRepository.findFirstWithDefaultCurrency()
                    .orElseThrow(() -> new IllegalStateException("System config not found"));

            if (config.isStrictCustomerRate()) {
                throw new ExchangeRateNotFoundException(src, tgt, rateType.name());
            }

            LocalDate minDate = rateDate.minusDays(MAX_FALLBACK_DAYS);
            for (RateSource source : List.of(RateSource.MANUAL, RateSource.TCMB)) {
                var fallback = exchangeRateRepository.findNearestPastRate(src, tgt, rateType.name(), source.name(), rateDate, minDate);
                if (fallback.isPresent()) {
                    log.warn("Müşteri [{}] için CUSTOMER fallback kur kullanıldı: {}/{} type={} requested={} used={}",
                            customerId, src, tgt, rateType, rateDate, fallback.get().getRateDate());
                    return new ExchangeRateDto(src, tgt, fallback.get().getRate(), fallback.get().getRateDate(),
                            rateType.name(), source.name(), true);
                }
            }
            throw new ExchangeRateNotFoundException(src, tgt, rateType.name());
        }

        var exact = exchangeRateRepository.findExactRate(
                src, tgt, rateDate, rateType, rateSource);
        if (exact.isPresent()) {
            var er = exact.get();
            return new ExchangeRateDto(src, tgt, er.getRate(), er.getRateDate(),
                    rateType.name(), rateSource.name(), false);
        }

        LocalDate minDate = rateDate.minusDays(MAX_FALLBACK_DAYS);
        var fallback = exchangeRateRepository
                .findNearestPastRate(
                        src, tgt, rateType.name(), rateSource.name(), rateDate, minDate)
                .orElseThrow(() -> new ExchangeRateNotFoundException(src, tgt, rateType.name()));

        log.warn("Müşteri [{}] için fallback kur kullanıldı: {}/{} type={} requested={} used={}",
                customerId, src, tgt, rateType, rateDate, fallback.getRateDate());

        return new ExchangeRateDto(src, tgt, fallback.getRate(), fallback.getRateDate(),
                rateType.name(), rateSource.name(), true);
    }

    // ── Customer Exchange Rates CRUD ──────────────────────────────────────────

    @Transactional
    public CustomerExchangeRateDto upsertCustomerRate(Long customerId, CustomerExchangeRateRequest request, Long userId) {
        String sourceCode = request.sourceCurrency().trim().toUpperCase();
        String targetCode = request.targetCurrency().trim().toUpperCase();

        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        var source = currencyRepository.findByCodeAndActiveTrue(sourceCode)
                .orElseThrow(() -> new IllegalArgumentException("Source currency not found: " + sourceCode));
        var target = currencyRepository.findByCodeAndActiveTrue(targetCode)
                .orElseThrow(() -> new IllegalArgumentException("Target currency not found: " + targetCode));

        // Para birimi izin kontrolü
        customerCurrencyValidator.validateCurrencyPermission(customerId, source.getId());
        customerCurrencyValidator.validateCurrencyPermission(customerId, target.getId());

        var existing = customerExchangeRateRepository.findExactRate(
                customerId, sourceCode, targetCode, request.rateDate(), request.rateType());

        CustomerExchangeRate saved;
        AuditActionType action;
        BigDecimal oldRate = null;

        if (existing.isPresent()) {
            var record = existing.get();
            oldRate = record.getRate();
            if (oldRate.compareTo(request.rate()) == 0) {
                return toCustomerRateDto(record, AuditActionType.UPDATE, oldRate);
            }
            record.setRate(request.rate());
            record.setCreatedBy(userId);
            saved = customerExchangeRateRepository.save(record);
            action = AuditActionType.UPDATE;
        } else {
            var newRate = CustomerExchangeRate.builder()
                    .customer(customer)
                    .sourceCurrency(source)
                    .targetCurrency(target)
                    .rateDate(request.rateDate())
                    .rateType(request.rateType())
                    .rate(request.rate())
                    .createdBy(userId)
                    .build();
            saved = customerExchangeRateRepository.save(newRate);
            action = AuditActionType.INSERT;
        }

        // Değişiklikleri ExchangeRateAuditLog'a yaz
        auditLogRepository.save(ExchangeRateAuditLog.builder()
                .customerExchangeRate(saved)
                .actionType(action)
                .oldRate(oldRate)
                .newRate(saved.getRate())
                .userId(userId)
                .build());

        // Cache temizle
        cacheService.evict(sourceCode, targetCode, request.rateDate());

        return toCustomerRateDto(saved, action, oldRate);
    }

    public CustomerExchangeRateDto getCustomerRateQuery(
            Long customerId,
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate,
            RateType rateType) {

        var sourceCode = sourceCurrency.trim().toUpperCase();
        var targetCode = targetCurrency.trim().toUpperCase();

        var rate = customerExchangeRateRepository.findExactRate(
                customerId, sourceCode, targetCode, rateDate, rateType)
                .orElseThrow(() -> new ExchangeRateNotFoundException(sourceCode, targetCode, rateType.name()));

        return toCustomerRateDto(rate, AuditActionType.INSERT, null);
    }

    private CustomerExchangeRateDto toCustomerRateDto(CustomerExchangeRate entity, AuditActionType action, BigDecimal oldRate) {
        return CustomerExchangeRateDto.builder()
                .id(entity.getId())
                .customerId(entity.getCustomer().getId())
                .sourceCurrency(entity.getSourceCurrency().getCode())
                .targetCurrency(entity.getTargetCurrency().getCode())
                .rateDate(entity.getRateDate())
                .rateType(entity.getRateType())
                .rate(entity.getRate())
                .action(action)
                .previousRate(oldRate)
                .build();
    }

    // ── Kur Farkı Tercihi ────────────────────────────────────────────────────

    /**
     * Müşterinin {@code exchangeDiffPreference} ayarına göre kur farkı hesaplamasının
     * bu fatura veya döngü için çalışıp çalışmayacağına karar verir.
     */
    public boolean shouldCalculateExchangeDiff(ExchangeDiffPreference preference,
                                               ExchangeDiffContext context) {
        return switch (preference) {
            case NONE -> false;
            case PER_INVOICE -> context == ExchangeDiffContext.PER_INVOICE;
            case MONTHLY     -> context == ExchangeDiffContext.MONTHLY;
        };
    }

    /**
     * Kur farkı hesaplamasının tetiklendiği bağlamı temsil eder.
     */
    public enum ExchangeDiffContext {
        /** Her fatura kesildiğinde tetiklenir. */
        PER_INVOICE,
        /** Ay sonunda toplu mahsup döngüsünde tetiklenir. */
        MONTHLY
    }
}
