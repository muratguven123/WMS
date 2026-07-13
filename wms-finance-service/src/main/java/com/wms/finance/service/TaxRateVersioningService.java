package com.wms.finance.service;

import com.wms.finance.dto.TaxRateResponse;
import com.wms.finance.dto.TaxRateUpdateRequest;
import com.wms.finance.dto.TaxRateVersionResult;
import com.wms.finance.entity.TaxRate;
import com.wms.finance.entity.TaxRateAuditLog;
import com.wms.finance.entity.enums.TaxRateAuditActionType;
import com.wms.finance.exception.TaxRateConflictException;
import com.wms.finance.exception.TaxRateNotFoundException;
import com.wms.finance.repository.TaxRateAuditLogRepository;
import com.wms.finance.repository.TaxRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxRateVersioningService {

    private final TaxRateRepository taxRateRepository;
    private final TaxRateAuditLogRepository auditLogRepository;

    @Transactional
    public TaxRateVersionResult updateRate(TaxRateUpdateRequest request, Long userId) {
        TaxRate existing = taxRateRepository.findById(request.taxRateId())
                .orElseThrow(() -> new TaxRateNotFoundException(
                        "TaxRate not found: " + request.taxRateId()));

        validateUpdatePreconditions(existing, request.effectiveDate());
        validateNoConflict(existing, request.effectiveDate());

        LocalDate closeDate = request.effectiveDate().minusDays(1);
        existing.setEndDate(closeDate);
        TaxRate savedExpired = taxRateRepository.save(existing);
        // Flush so uq_tax_rate_open_active sees the closed end_date before the new open row is inserted.
        taxRateRepository.flush();

        TaxRate savedNew = taxRateRepository.save(buildNewVersion(existing, request));

        auditLogRepository.save(TaxRateAuditLog.builder()
                .taxRate(savedNew)
                .actionType(TaxRateAuditActionType.UPDATE_RATE)
                .oldRate(savedExpired.getRate())
                .newRate(savedNew.getRate())
                .userId(userId)
                .build());

        log.info("[TAX-RATE] Versioned: expiredId={}, newId={}, effectiveDate={}",
                savedExpired.getId(), savedNew.getId(), request.effectiveDate());

        return new TaxRateVersionResult(
                TaxRateResponse.from(savedExpired),
                TaxRateResponse.from(savedNew));
    }

    private void validateUpdatePreconditions(TaxRate existing, LocalDate effectiveDate) {
        if (!existing.isActive()) {
            throw new TaxRateConflictException(
                    "Cannot update an inactive tax rate: " + existing.getId());
        }
        if (existing.getEndDate() != null) {
            throw new TaxRateConflictException(
                    "Cannot update an already expired tax rate: " + existing.getId());
        }
        if (!effectiveDate.isAfter(existing.getStartDate())) {
            throw new TaxRateConflictException(
                    "effectiveDate (%s) must be after the existing startDate (%s)"
                            .formatted(effectiveDate, existing.getStartDate()));
        }
    }

    private void validateNoConflict(TaxRate existing, LocalDate effectiveDate) {
        boolean conflicts = taxRateRepository.existsOverlappingRateExcluding(
                existing.getTaxType().getId(),
                existing.getCountryId(),
                existing.getLocationId(),
                existing.getCustomerId(),
                existing.getProductType(),
                existing.getOperationType(),
                effectiveDate,
                LocalDate.of(9999, 12, 31),
                existing.getId());

        if (conflicts) {
            throw new TaxRateConflictException(
                    "Overlapping tax rate already exists for the same scope starting %s"
                            .formatted(effectiveDate));
        }
    }

    private TaxRate buildNewVersion(TaxRate source, TaxRateUpdateRequest request) {
        return TaxRate.builder()
                .taxType(source.getTaxType())
                .countryId(source.getCountryId())
                .locationId(source.getLocationId())
                .customerId(source.getCustomerId())
                .productType(source.getProductType())
                .operationType(source.getOperationType())
                .rate(request.newRate())
                .startDate(request.effectiveDate())
                .endDate(null)
                .active(true)
                .build();
    }
}
