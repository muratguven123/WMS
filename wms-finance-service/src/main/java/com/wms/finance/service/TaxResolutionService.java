package com.wms.finance.service;

import com.wms.finance.entity.TaxRate;
import com.wms.finance.entity.TaxType;
import com.wms.finance.exception.TaxResolutionException;
import com.wms.finance.repository.TaxRateRepository;
import com.wms.finance.repository.TaxTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxResolutionService {

    private final TaxTypeRepository taxTypeRepository;
    private final TaxRateRepository taxRateRepository;

    public BigDecimal resolveTaxRate(
            String taxTypeCode,
            LocalDate transactionDate,
            TaxResolutionContext context) {

        TaxType taxType = taxTypeRepository.findByCode(taxTypeCode)
                .filter(TaxType::isActive)
                .orElseThrow(() -> new TaxResolutionException(
                        "Active tax type not found for code: " + taxTypeCode));

        List<TaxRate> candidates = taxRateRepository.findApplicableRates(
                taxType.getId(),
                context.countryId(),
                context.locationId(),
                context.customerId(),
                context.productType(),
                context.operationType(),
                transactionDate);

        log.debug("Tax resolution: taxType={}, date={}, candidates={}",
                taxTypeCode, transactionDate, candidates.size());

        return bestMatch(candidates, context)
                .map(TaxRate::getRate)
                .orElseThrow(() -> new TaxResolutionException(
                        "No applicable tax rate found for taxType=%s, country=%s, date=%s"
                                .formatted(taxTypeCode, context.countryId(), transactionDate)));
    }

    private Optional<TaxRate> bestMatch(List<TaxRate> candidates, TaxResolutionContext ctx) {
        return candidates.stream()
                .filter(r -> matchesScope(r, ctx))
                .max(Comparator
                        .comparingInt((TaxRate r) -> specificity(r, ctx))
                        .thenComparing(TaxRate::getStartDate));
    }

    private boolean matchesScope(TaxRate rate, TaxResolutionContext ctx) {
        if (rate.getLocationId() != null && !Objects.equals(rate.getLocationId(), ctx.locationId())) {
            return false;
        }
        if (rate.getCustomerId() != null && !Objects.equals(rate.getCustomerId(), ctx.customerId())) {
            return false;
        }
        if (rate.getProductType() != null && !Objects.equals(rate.getProductType(), ctx.productType())) {
            return false;
        }
        return rate.getOperationType() == null
                || Objects.equals(rate.getOperationType(), ctx.operationType());
    }

    private int specificity(TaxRate rate, TaxResolutionContext ctx) {
        int score = 0;
        if (rate.getLocationId() != null && Objects.equals(rate.getLocationId(), ctx.locationId())) {
            score += 4;
        }
        if (rate.getCustomerId() != null && Objects.equals(rate.getCustomerId(), ctx.customerId())) {
            score += 2;
        }
        if (rate.getProductType() != null && Objects.equals(rate.getProductType(), ctx.productType())) {
            score += 1;
        }
        return score;
    }
}
