package com.wms.finance.service;

import com.wms.finance.dto.ExchangeDifferenceTaxRequest;
import com.wms.finance.dto.TaxCalculateRequest;
import com.wms.finance.dto.TaxCalculateResponse;
import com.wms.finance.dto.TaxRateListItemDto;
import com.wms.finance.entity.TaxRate;
import com.wms.finance.entity.TaxType;
import com.wms.finance.exception.TaxResolutionException;
import com.wms.finance.repository.TaxRateRepository;
import com.wms.finance.repository.TaxTypeRepository;
import com.wms.finance.tax.audit.entity.TransactionType;
import com.wms.finance.tax.engine.model.TaxEngineContext;
import com.wms.finance.tax.engine.model.TaxCalculationResultDto;
import com.wms.finance.tax.engine.model.TaxLineItem;
import com.wms.finance.tax.engine.model.TaxRule;
import com.wms.finance.tax.engine.model.TaxStrategyType;
import com.wms.finance.tax.engine.service.TaxEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxQueryService {

    private final TaxRateRepository taxRateRepository;
    private final TaxTypeRepository taxTypeRepository;
    private final TaxEngineService taxEngineService;
    private final TaxResolutionService taxResolutionService;

    @Transactional(readOnly = true)
    public Page<TaxRateListItemDto> listRates(
            Long countryId,
            Long locationId,
            String taxTypeCode,
            Boolean active,
            Pageable pageable) {

        Specification<TaxRate> spec = (root, query, cb) -> cb.conjunction();

        List<Specification<TaxRate>> specs = new ArrayList<>();
        if (countryId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("countryId"), countryId));
        }
        if (locationId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("locationId"), locationId));
        }
        if (active != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (taxTypeCode != null && !taxTypeCode.isBlank()) {
            specs.add((root, query, cb) -> cb.equal(root.join("taxType").get("code"), taxTypeCode));
        }

        Specification<TaxRate> combined = specs.stream()
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());

        return taxRateRepository.findAll(combined, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public TaxCalculateResponse calculate(TaxCalculateRequest request) {
        boolean inclusive = "INCLUSIVE".equalsIgnoreCase(request.mode());
        TaxStrategyType strategyType = inclusive ? TaxStrategyType.INCLUSIVE : TaxStrategyType.EXCLUSIVE;

        TaxType taxType = taxTypeRepository.findByCode(request.taxTypeCode())
                .filter(TaxType::isActive)
                .orElseThrow(() -> new TaxResolutionException(
                        "Vergi tipi bulunamadı: " + request.taxTypeCode()));
        if (!taxType.isActive()) {
            throw new TaxResolutionException("Vergi tipi aktif değil: " + request.taxTypeCode());
        }

        LocalDate onDate = request.transactionDate() != null
                ? request.transactionDate()
                : LocalDate.now();

        var context = new TaxResolutionContext(
                request.countryId(),
                request.locationId(),
                request.customerId(),
                request.productType(),
                request.operationType());

        BigDecimal ratePercent = taxResolutionService.resolveTaxRate(
                request.taxTypeCode(), onDate, context);

        Long lineId = 1L;
        TaxLineItem item = new TaxLineItem(lineId, "UI simulation", request.amount());
        TaxRule rule = TaxRule.of(request.taxTypeCode(), strategyType, "UI_CALCULATOR");

        TaxEngineContext ctx = new TaxEngineContext(
                TransactionType.TRANSACTION_FEE,
                1L,
                List.of(item),
                List.of(rule),
                inclusive,
                request.countryId(),
                request.locationId(),
                request.customerId(),
                request.productType(),
                request.operationType(),
                onDate);

        List<TaxCalculationResultDto> results = taxEngineService.calculate(ctx);
        TaxCalculationResultDto result = results.getFirst();

        return new TaxCalculateResponse(
                result.baseAmount(),
                result.taxAmount(),
                result.grandTotal(),
                result.taxTypeCode(),
                ratePercent,
                inclusive);
    }

    @Transactional
    public TaxCalculateResponse calculateExchangeDifferenceTax(ExchangeDifferenceTaxRequest request) {
        TaxCalculationResultDto result = taxEngineService.calculateExchangeDifferenceTax(
                request.exchangeDifferenceAmount(),
                request.taxTypeCode(),
                request.date(),
                request.locationId(),
                request.countryId());

        return new TaxCalculateResponse(
                result.baseAmount(),
                result.taxAmount(),
                result.grandTotal(),
                result.taxTypeCode(),
                null,
                false);
    }

    private TaxRateListItemDto toListItem(TaxRate rate) {
        return new TaxRateListItemDto(
                rate.getId(),
                rate.getTaxType().getCode(),
                rate.getCountryId(),
                rate.getLocationId(),
                rate.getCustomerId(),
                rate.getProductType(),
                rate.getOperationType(),
                rate.getRate(),
                rate.getStartDate(),
                rate.getEndDate(),
                rate.isActive(),
                rate.getCreatedAt());
    }
}
