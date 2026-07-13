package com.wms.finance.service;

import com.wms.events.DomainEvent;
import com.wms.finance.entity.Currency;
import com.wms.finance.entity.LocationCurrencySetting;
import com.wms.finance.entity.TaxRate;
import com.wms.finance.repository.CurrencyRepository;
import com.wms.finance.repository.LocationCurrencySettingRepository;
import com.wms.finance.repository.TaxRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProvisioningService {

    private static final Map<Long, String> COUNTRY_CURRENCY_CODES = Map.of(
            1L, "TRY",
            2L, "EUR"
    );

    private final LocationCurrencySettingRepository locationCurrencySettingRepository;
    private final CurrencyRepository currencyRepository;
    private final TaxRateRepository taxRateRepository;

    @Transactional
    public void provisionFromEvent(DomainEvent event) {
        if (event.locationId() == null || event.companyId() == null) {
            return;
        }
        Long locationId = event.locationId();
        Long companyId = event.companyId();
        Long countryId = extractLong(event.payload(), "countryId");

        if (locationCurrencySettingRepository.findByLocationIdWithCurrency(locationId).isEmpty()) {
            String currencyCode = resolveCurrencyCode(countryId);
            Currency currency = currencyRepository.findByCodeAndActiveTrue(currencyCode)
                    .orElseGet(() -> currencyRepository.findByCodeAndActiveTrue("EUR")
                            .orElseThrow(() -> new IllegalStateException("No default currency configured")));

            locationCurrencySettingRepository.save(LocationCurrencySetting.builder()
                    .locationId(locationId)
                    .companyId(companyId)
                    .localCurrency(currency)
                    .build());
            log.info("LocationCurrencySetting created for locationId={}", locationId);
        }

        if (countryId != null) {
            copyCountryTaxRates(countryId, locationId);
        }
    }

    private void copyCountryTaxRates(Long countryId, Long locationId) {
        List<TaxRate> countryRates = taxRateRepository.findByCountryIdAndLocationIdIsNullAndActiveTrue(countryId);
        for (TaxRate source : countryRates) {
            if (taxRateRepository.findActiveOpenRate(
                    source.getTaxType().getId(),
                    countryId,
                    locationId,
                    source.getCustomerId(),
                    source.getProductType(),
                    source.getOperationType()).isPresent()) {
                continue;
            }
            TaxRate copy = TaxRate.builder()
                    .taxType(source.getTaxType())
                    .countryId(countryId)
                    .locationId(locationId)
                    .customerId(source.getCustomerId())
                    .productType(source.getProductType())
                    .operationType(source.getOperationType())
                    .rate(source.getRate())
                    .startDate(LocalDate.now())
                    .active(true)
                    .build();
            taxRateRepository.save(copy);
        }
    }

    private String resolveCurrencyCode(Long countryId) {
        if (countryId == null) {
            return "EUR";
        }
        return COUNTRY_CURRENCY_CODES.getOrDefault(countryId, "EUR");
    }

    private Long extractLong(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
