package com.wms.finance.service;

import com.wms.finance.dto.ErpTaxImportRequest;
import com.wms.finance.entity.TaxRate;
import com.wms.finance.entity.TaxType;
import com.wms.finance.exception.TaxResolutionException;
import com.wms.finance.repository.TaxRateRepository;
import com.wms.finance.repository.TaxTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP vergi bilgisi pull sonucunu tax_rates master data'ya yazar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpTaxImportService {

    private final TaxTypeRepository taxTypeRepository;
    private final TaxRateRepository taxRateRepository;

    @Transactional
    public TaxRate importRate(ErpTaxImportRequest request) {
        TaxType taxType = taxTypeRepository.findByCode(request.taxTypeCode())
                .orElseThrow(() -> new TaxResolutionException(
                        "Vergi tipi bulunamadı: " + request.taxTypeCode()));

        LocalDate start = request.validFrom() != null ? request.validFrom() : LocalDate.now();

        List<TaxRate> existing = taxRateRepository.findAll().stream()
                .filter(r -> r.getTaxType().getId().equals(taxType.getId()))
                .filter(r -> r.getCountryId().equals(request.countryId()))
                .filter(r -> java.util.Objects.equals(r.getLocationId(), request.locationId()))
                .filter(TaxRate::isActive)
                .filter(r -> r.getEndDate() == null || !r.getEndDate().isBefore(LocalDate.now()))
                .toList();

        for (TaxRate rate : existing) {
            if (rate.getRate().compareTo(request.rate()) == 0) {
                log.info("[ErpTaxImport] Oran zaten güncel: taxType={} rate={}",
                        request.taxTypeCode(), request.rate());
                return rate;
            }
            rate.setEndDate(start.minusDays(1));
            rate.setActive(false);
            taxRateRepository.save(rate);
        }

        TaxRate created = TaxRate.builder()
                .taxType(taxType)
                .countryId(request.countryId())
                .locationId(request.locationId())
                .rate(request.rate())
                .startDate(start)
                .endDate(request.validTo())
                .active(true)
                .build();

        TaxRate saved = taxRateRepository.save(created);
        log.info("[ErpTaxImport] Yeni oran yazıldı: id={} taxType={} rate={}",
                saved.getId(), request.taxTypeCode(), request.rate());
        return saved;
    }
}
