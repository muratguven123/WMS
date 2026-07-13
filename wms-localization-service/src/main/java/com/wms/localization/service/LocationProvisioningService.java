package com.wms.localization.service;

import com.wms.events.DomainEvent;
import com.wms.localization.entity.CountryFormatConfig;
import com.wms.localization.entity.LocationFormatOverride;
import com.wms.localization.repository.CountryFormatConfigRepository;
import com.wms.localization.repository.LocationFormatOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationProvisioningService {

    private final LocationFormatOverrideRepository locationFormatOverrideRepository;
    private final CountryFormatConfigRepository countryFormatConfigRepository;

    @Transactional
    public void provisionFromEvent(DomainEvent event) {
        if (event.locationId() == null) {
            return;
        }
        if (locationFormatOverrideRepository.existsByLocationId(event.locationId())) {
            return;
        }

        Long countryId = extractLong(event.payload(), "countryId");
        if (countryId == null) {
            return;
        }

        countryFormatConfigRepository.findByCountryId(countryId).ifPresent(countryConfig -> {
            LocationFormatOverride override = LocationFormatOverride.builder()
                    .locationId(event.locationId())
                    .dateFormat(countryConfig.getDateFormat())
                    .timeFormat(countryConfig.getTimeFormat())
                    .decimalSeparator(countryConfig.getDecimalSeparator())
                    .thousandSeparator(countryConfig.getThousandSeparator())
                    .build();
            locationFormatOverrideRepository.save(override);
            log.info("LocationFormatOverride created for locationId={}", event.locationId());
        });
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
